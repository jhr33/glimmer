package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.entity.Announcement;
import com.glimmer.entity.Notification;
import com.glimmer.entity.User;
import com.glimmer.mapper.AnnouncementMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.AnnouncementService;
import com.glimmer.service.dto.AnnouncementAdminVO;
import com.glimmer.service.dto.AnnouncementListVO;
import com.glimmer.service.dto.AnnouncementVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 公告服务实现
 * 见开发文档 §2.10
 */
@Slf4j
@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    /** 广播通知内容摘要最大长度 */
    private static final int SUMMARY_MAX_LENGTH = 100;
    /** 批量插入每批大小 */
    private static final int BATCH_SIZE = 500;

    private final AnnouncementMapper announcementMapper;
    private final UserMapper userMapper;

    public AnnouncementServiceImpl(AnnouncementMapper announcementMapper, UserMapper userMapper) {
        this.announcementMapper = announcementMapper;
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<AnnouncementListVO> getAnnouncementList(int page, int size) {
        Page<Announcement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, "published")
                .orderByDesc(Announcement::getCreatedAt);

        IPage<Announcement> result = announcementMapper.selectPage(pageParam, wrapper);
        List<AnnouncementListVO> list = result.getRecords().stream()
                .map(this::toListVO)
                .collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementVO getAnnouncementDetail(Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null || "taken_down".equals(announcement.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在或已下架");
        }
        return toVO(announcement);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publishAnnouncement(Long publisherId, String title, String content) {
        // 1. 插入公告
        Announcement announcement = new Announcement();
        announcement.setTitle(title);
        announcement.setContent(content);
        announcement.setPublisherId(publisherId);
        announcement.setStatus("published");
        announcementMapper.insert(announcement);

        // 2. 广播通知：向所有 status='active' 的用户发送 announcement 通知
        List<Long> activeUserIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .select(User::getId)
                        .eq(User::getStatus, "active"))
                .stream().map(User::getId).collect(Collectors.toList());

        if (!activeUserIds.isEmpty()) {
            String summary = content.length() > SUMMARY_MAX_LENGTH
                    ? content.substring(0, SUMMARY_MAX_LENGTH)
                    : content;
            LocalDateTime now = LocalDateTime.now();
            List<Notification> notifications = new ArrayList<>(activeUserIds.size());
            for (Long userId : activeUserIds) {
                Notification n = new Notification();
                n.setUserId(userId);
                n.setType("announcement");
                n.setTitle(title);
                n.setContent(summary);
                n.setRefType("announcement");
                n.setRefId(announcement.getId());
                n.setIsRead(0);
                n.setCreatedAt(now);
                notifications.add(n);
            }
            // 批量插入优化：分批 saveBatch，避免逐条插入
            for (int i = 0; i < notifications.size(); i += BATCH_SIZE) {
                int to = Math.min(i + BATCH_SIZE, notifications.size());
                Db.saveBatch(notifications.subList(i, to));
            }
            log.info("公告广播通知完成: announcementId={}, notified={}", announcement.getId(), activeUserIds.size());
        }

        log.info("公告发布成功: id={}, publisherId={}", announcement.getId(), publisherId);
        return announcement.getId();
    }

    @Override
    public PageResult<AnnouncementAdminVO> getAnnouncementListForAdmin(String status, int page, int size) {
        Page<Announcement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .eq(StringUtils.hasText(status), Announcement::getStatus, status)
                .orderByDesc(Announcement::getCreatedAt);

        IPage<Announcement> result = announcementMapper.selectPage(pageParam, wrapper);
        List<AnnouncementAdminVO> list = result.getRecords().stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void takeDownAnnouncement(Long announcementId) {
        Announcement announcement = announcementMapper.selectById(announcementId);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        if (!"published".equals(announcement.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "仅已发布的公告可下架");
        }
        announcement.setStatus("taken_down");
        announcementMapper.updateById(announcement);
        log.info("公告下架成功: id={}", announcementId);
    }

    private AnnouncementListVO toListVO(Announcement announcement) {
        AnnouncementListVO vo = new AnnouncementListVO();
        vo.setId(announcement.getId());
        vo.setTitle(announcement.getTitle());
        vo.setCreatedAt(announcement.getCreatedAt());
        return vo;
    }

    private AnnouncementVO toVO(Announcement announcement) {
        AnnouncementVO vo = new AnnouncementVO();
        vo.setId(announcement.getId());
        vo.setTitle(announcement.getTitle());
        vo.setContent(announcement.getContent());
        vo.setPublisherId(announcement.getPublisherId());
        vo.setCreatedAt(announcement.getCreatedAt());
        return vo;
    }

    private AnnouncementAdminVO toAdminVO(Announcement announcement) {
        AnnouncementAdminVO vo = new AnnouncementAdminVO();
        vo.setId(announcement.getId());
        vo.setTitle(announcement.getTitle());
        vo.setContent(announcement.getContent());
        vo.setPublisherId(announcement.getPublisherId());
        vo.setStatus(announcement.getStatus());
        vo.setCreatedAt(announcement.getCreatedAt());
        return vo;
    }
}

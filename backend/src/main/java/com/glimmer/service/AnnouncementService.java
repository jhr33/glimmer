package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.AnnouncementAdminVO;
import com.glimmer.service.dto.AnnouncementListVO;
import com.glimmer.service.dto.AnnouncementVO;

/**
 * 公告服务
 * 见开发文档 §2.10
 */
public interface AnnouncementService {

    /**
     * 分页查询已发布公告（按 created_at 倒序）
     */
    PageResult<AnnouncementListVO> getAnnouncementList(int page, int size);

    /**
     * 公告详情（下架的公告返回 404）
     */
    AnnouncementVO getAnnouncementDetail(Long id);

    /**
     * 发布公告（含广播通知）。返回公告ID
     */
    Long publishAnnouncement(Long publisherId, String title, String content);

    /**
     * 管理员公告列表（含下架），可按 status 筛选
     */
    PageResult<AnnouncementAdminVO> getAnnouncementListForAdmin(String status, int page, int size);

    /**
     * 下架公告
     */
    void takeDownAnnouncement(Long announcementId);
}

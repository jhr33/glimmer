package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.NotificationVO;

/**
 * 通知服务
 * 见开发文档 §2.11
 */
public interface NotificationService {

    /**
     * 分页查询通知列表（按 created_at 倒序）
     */
    PageResult<NotificationVO> getNotifications(Long userId, int page, int size);

    /**
     * 返回未读数量
     */
    long getUnreadCount(Long userId);

    /**
     * 标记单条已读（校验 user_id 匹配）
     */
    void markAsRead(Long userId, Long notificationId);

    /**
     * 全部标记已读
     */
    void markAllAsRead(Long userId);

    /**
     * 内部方法：发送通知（供其他 Service 调用，不暴露为接口）
     *
     * @param userId  接收用户ID
     * @param type    通知类型: report_result/feedback_reply/announcement/system
     * @param title   标题
     * @param content 内容
     * @param refType 关联类型
     * @param refId   关联ID
     */
    void sendNotification(Long userId, String type, String title, String content, String refType, Long refId);

    /**
     * 内部方法：发送通知（带额外信息）
     *
     * @param userId  接收用户ID
     * @param type    通知类型
     * @param title   标题
     * @param content 内容
     * @param refType 关联类型
     * @param refId   关联ID
     * @param extra   额外信息（JSON格式）
     */
    void sendNotification(Long userId, String type, String title, String content, String refType, Long refId, String extra);
}

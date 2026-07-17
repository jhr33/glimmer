package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.LetterVO;

/**
 * 信件服务
 * 见开发文档 §2.4
 */
public interface LetterService {

    /**
     * 写信（消耗1代币）
     *
     * @param senderId            发送者ID
     * @param receiverId          收信人ID
     * @param content             内容
     * @param sourceBottleReplyId 来源漂流瓶回复ID
     */
    void writeLetter(Long senderId, Long receiverId, String content, Long sourceBottleReplyId);

    /**
     * 回复信件
     */
    void replyLetter(Long userId, Long letterId, String content);

    /**
     * 收件箱
     */
    PageResult<LetterVO> getInbox(Long userId, int page, int size);

    /**
     * 发件箱
     */
    PageResult<LetterVO> getSent(Long userId, int page, int size);

    /**
     * 信件详情（校验为 sender 或 receiver）
     */
    LetterVO getLetterDetail(Long userId, Long letterId);

    /**
     * 感谢信件（给发送者 +1代币 +1萤火）
     */
    void thankLetter(Long userId, Long letterId);

    /**
     * 标记信件为已读
     */
    void markAsRead(Long userId, Long letterId);
}

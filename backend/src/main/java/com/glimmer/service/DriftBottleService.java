package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.BottlePickVO;
import com.glimmer.service.dto.BottleReplyVO;
import com.glimmer.service.dto.BottleSummaryVO;
import com.glimmer.service.dto.BottleVO;

import java.util.List;

/**
 * 漂流瓶服务
 * 见开发文档 §2.3
 */
public interface DriftBottleService {

    /**
     * 扔漂流瓶
     */
    void throwBottle(Long userId, String content);

    /**
     * 捡漂流瓶，返回瓶子基本信息（不含内容）。无可捡的瓶子返回 null。
     *
     * @param mode 捡瓶模式："public" 公海（默认，捞他人的瓶子）/ "private" 私海（仅捞自己的瓶子）
     */
    BottlePickVO pickBottle(Long userId, String mode);

    /**
     * 查看瓶子内容（校验已捡到），并标记 opened=1
     */
    BottleVO getBottleContent(Long userId, Long bottleId);

    /**
     * 放回瓶子（校验已捡到，不做状态变更）
     */
    void releaseBottle(Long userId, Long bottleId);

    /**
     * 回复漂流瓶（每人限1次）
     */
    void replyBottle(Long userId, Long bottleId, String content);

    /**
     * 查看我的瓶子回复（仅瓶主可看）
     */
    List<BottleReplyVO> getBottleReplies(Long userId, Long bottleId);

    /**
     * 感谢漂流瓶（给瓶子作者 +1代币 +1萤火）
     */
    void thankBottle(Long userId, Long bottleId);

    /**
     * 感谢瓶子回复（给回复者 +1代币 +1萤火）
     */
    void thankBottleReply(Long userId, Long replyId);

    /**
     * 沉底自己的瓶子
     */
    void sinkBottle(Long userId, Long bottleId);

    /**
     * 我扔出的瓶子列表，分页（支持关键词和时间范围筛选）
     *
     * @param keyword   关键词（对 content 做 LIKE 模糊匹配，null/空不筛选）
     * @param timeRange 时间范围：today 今天 / week 近7天 / month 近30天 / custom 自定义(startDate~endDate) / null 或其它 不限
     * @param startDate 自定义开始日期（yyyy-MM-dd，timeRange=custom 时生效）
     * @param endDate   自定义结束日期（yyyy-MM-dd，timeRange=custom 时生效）
     */
    PageResult<BottleVO> getMyBottles(Long userId, int page, int size,
                                      String keyword, String timeRange,
                                      String startDate, String endDate);

    /**
     * 游客可看的漂流瓶列表（仅摘要，不含内容）
     */
    PageResult<BottleSummaryVO> getBottleList(int page, int size);
}

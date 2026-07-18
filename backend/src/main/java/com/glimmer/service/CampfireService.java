package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.CampfireMessageVO;
import com.glimmer.service.dto.CampfireVO;

import java.util.List;

/**
 * 篝火服务接口
 * 见开发文档 §2.5 / §4.8
 */
public interface CampfireService {

    /**
     * 篝火列表（系统默认 + 我创建的 + 我加入的）
     */
    List<CampfireVO> getCampfireList(Long userId);

    /**
     * 创建篝火（消耗代币：10人→1，20人→2，30人→3）
     */
    CampfireVO createCampfire(Long userId, String name, int maxMembers);

    /**
     * 篝火详情（含成员数）
     */
    CampfireVO getCampfireDetail(Long userId, Long campfireId);

    /**
     * 历史消息分页（校验用户是该篝火成员）
     */
    PageResult<CampfireMessageVO> getHistoryMessages(Long userId, Long campfireId, int page, int size);

    /**
     * 加入篝火
     */
    void joinCampfire(Long userId, Long campfireId);

    /**
     * 退出篝火（创建者不允许退出）
     */
    void leaveCampfire(Long userId, Long campfireId);

    /**
     * 发送消息（通过 WebSocket 推送到 /topic/campfire/{campfireId}）
     */
    CampfireMessageVO sendMessage(Long userId, Long campfireId, String content);

    /**
     * 熄灭篝火（仅创建者可操作，系统默认篝火不可熄灭）
     */
    void extinguishCampfire(Long userId, Long campfireId);
}

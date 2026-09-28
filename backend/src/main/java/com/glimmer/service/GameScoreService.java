package com.glimmer.service;

import com.glimmer.service.dto.GameAliasVO;
import com.glimmer.service.dto.LeaderboardItemVO;
import com.glimmer.service.dto.MyBestScoreVO;
import com.glimmer.service.dto.SaveAliasRequest;
import com.glimmer.service.dto.SubmitScoreRequest;
import com.glimmer.service.dto.SubmitScoreResultVO;

import java.util.List;

/**
 * 小游戏成绩服务
 */
public interface GameScoreService {

    /**
     * 提交一局游戏成绩
     *
     * @param userId 当前登录用户ID
     * @param req    成绩内容
     * @return 提交结果（含是否刷新个人纪录、最新名次）
     */
    SubmitScoreResultVO submitScore(Long userId, SubmitScoreRequest req);

    /**
     * 排行榜（每个用户只展示最高分一局）
     *
     * @param gameType      游戏类型
     * @param limit         条数上限
     * @param currentUserId 当前登录用户ID（游客传 null，用于高亮当前玩家行）
     */
    List<LeaderboardItemVO> getLeaderboard(String gameType, int limit, Long currentUserId);

    /**
     * 查询我的最佳成绩与名次
     */
    MyBestScoreVO getMyBest(Long userId, String gameType);

    /**
     * 查询我在某游戏下已保存的匿名代号（未设置返回 alias=null）
     */
    GameAliasVO getMyAlias(Long userId, String gameType);

    /**
     * 设置/修改我在某游戏下的匿名代号（用户×游戏唯一，重复设置为更新）
     */
    GameAliasVO saveMyAlias(Long userId, SaveAliasRequest req);
}

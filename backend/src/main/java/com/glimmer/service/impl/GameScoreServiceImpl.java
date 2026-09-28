package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.entity.GamePlayerAlias;
import com.glimmer.entity.GameScore;
import com.glimmer.entity.User;
import com.glimmer.mapper.GamePlayerAliasMapper;
import com.glimmer.mapper.GameScoreMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.GameScoreService;
import com.glimmer.service.dto.GameAliasVO;
import com.glimmer.service.dto.LeaderboardItemVO;
import com.glimmer.service.dto.MyBestScoreVO;
import com.glimmer.service.dto.SaveAliasRequest;
import com.glimmer.service.dto.SubmitScoreRequest;
import com.glimmer.service.dto.SubmitScoreResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 小游戏成绩服务实现
 * 首期仅贪吃蛇（snake），game_type 白名单为后续小游戏预留扩展
 */
@Slf4j
@Service
public class GameScoreServiceImpl implements GameScoreService {

    /** 当前已上线的游戏类型白名单 */
    private static final Set<String> SUPPORTED_GAMES = Set.of("snake");

    /** 排行榜最大返回条数（防止前端传超大 limit 拖慢查询） */
    private static final int MAX_LIMIT = 100;

    /** 单局得分/时长/等级的合法上限（基础防刷与脏数据拦截） */
    private static final int MAX_SCORE = 1_000_000;
    private static final int MAX_DURATION_SECONDS = 24 * 60 * 60;
    private static final int MAX_LEVEL = 999;

    /**
     * 匿名代号合法规则：2-12 位，仅允许中文、英文、数字、下划线。
     * 游戏代号是玩家手动输入的对外展示内容，白名单字符可直接挡住表情/特殊符号/控制字符
     */
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9_]{2,12}$");

    private final GameScoreMapper gameScoreMapper;
    private final GamePlayerAliasMapper gamePlayerAliasMapper;
    private final UserMapper userMapper;

    public GameScoreServiceImpl(GameScoreMapper gameScoreMapper,
                                GamePlayerAliasMapper gamePlayerAliasMapper,
                                UserMapper userMapper) {
        this.gameScoreMapper = gameScoreMapper;
        this.gamePlayerAliasMapper = gamePlayerAliasMapper;
        this.userMapper = userMapper;
    }

    @Override
    public SubmitScoreResultVO submitScore(Long userId, SubmitScoreRequest req) {
        // 1. 参数归一化与白名单校验
        String gameType = req.getGameType() == null ? "" : req.getGameType().trim();
        if (!SUPPORTED_GAMES.contains(gameType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的游戏类型");
        }
        int score = normalize(req.getScore(), 0, MAX_SCORE);
        int level = normalize(req.getLevel(), 1, MAX_LEVEL);
        int durationSeconds = normalize(req.getDurationSeconds(), 0, MAX_DURATION_SECONDS);

        // 2. 查询提交前的个人最高分（用于判断是否刷新纪录）
        GameScore previousBest = selectBestRecord(userId, gameType);
        boolean newPersonalBest = previousBest == null || score > previousBest.getScore();

        // 3. 昵称快照：优先昵称，其次用户名，兜底"匿名旅人"
        User user = userMapper.selectById(userId);
        String displayName = "匿名旅人";
        if (user != null) {
            if (user.getNickname() != null && !user.getNickname().isBlank()) {
                displayName = user.getNickname();
            } else if (user.getUsername() != null && !user.getUsername().isBlank()) {
                displayName = user.getUsername();
            }
        }

        // 3.1 匿名上榜：使用玩家在"本游戏"手动设置的匿名代号做快照。
        //     该代号与平台统一匿名昵称相互独立，仅写入游戏记录供排行榜展示
        boolean anonymous = Boolean.TRUE.equals(req.getAnonymous());
        String anonymousName = null;
        String leaderboardName = displayName;
        if (anonymous) {
            GamePlayerAlias aliasRecord = selectAlias(userId, gameType);
            if (aliasRecord == null) {
                // 前端正常流程会先保存代号；未保存直接提交匿名成绩属于异常请求
                throw new BusinessException(ErrorCode.PARAM_ERROR, "请先设置游戏匿名代号");
            }
            anonymousName = aliasRecord.getAlias();
            leaderboardName = anonymousName;
        }

        // 4. 落库（每局保留一条明细，排行榜聚合取最高分）
        GameScore record = new GameScore();
        record.setUserId(userId);
        record.setGameType(gameType);
        record.setDisplayName(displayName);
        record.setAnonymous(anonymous);
        record.setAnonymousName(anonymousName);
        record.setScore(score);
        record.setLevel(level);
        record.setDurationSeconds(durationSeconds);
        gameScoreMapper.insert(record);

        // 5. 计算提交后的名次（0 分不参与有意义的排名，直接返回 null 由前端处理）
        Integer rank = score > 0 ? gameScoreMapper.selectRankByScore(gameType, score) : null;

        SubmitScoreResultVO result = new SubmitScoreResultVO();
        result.setId(record.getId());
        result.setScore(score);
        result.setLevel(level);
        result.setDurationSeconds(durationSeconds);
        result.setNewPersonalBest(newPersonalBest);
        result.setRank(rank);
        result.setAnonymous(anonymous);
        result.setDisplayName(leaderboardName);
        log.info("[小游戏] 成绩提交: userId={}, game={}, score={}, level={}, anonymous={}, newBest={}",
                userId, gameType, score, level, anonymous, newPersonalBest);
        return result;
    }

    @Override
    public List<LeaderboardItemVO> getLeaderboard(String gameType, int limit, Long currentUserId) {
        if (!SUPPORTED_GAMES.contains(gameType == null ? "" : gameType.trim())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的游戏类型");
        }
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, MAX_LIMIT);
        List<GameScore> records = gameScoreMapper.selectLeaderboard(gameType.trim(), safeLimit);
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        // 名次按返回顺序从 1 开始；高亮当前登录用户所在行
        final int[] rankHolder = {1};
        return records.stream().map(record -> {
            boolean isMe = currentUserId != null && currentUserId.equals(record.getUserId());
            boolean anon = Boolean.TRUE.equals(record.getAnonymous());
            LeaderboardItemVO vo = new LeaderboardItemVO();
            vo.setRank(rankHolder[0]++);
            vo.setScore(record.getScore());
            vo.setLevel(record.getLevel());
            vo.setDurationSeconds(record.getDurationSeconds());
            vo.setCreatedAt(record.getCreatedAt());
            vo.setCurrentUser(isMe);
            vo.setAnonymous(anon);
            if (anon) {
                // 服务端响应层脱敏：匿名行只下发匿名昵称快照，
                // 真实 userId 置空，避免抓包/接口反查出真实身份
                vo.setUserId(null);
                vo.setDisplayName(record.getAnonymousName() != null
                        && !record.getAnonymousName().isBlank()
                        ? record.getAnonymousName() : "匿名旅人");
            } else {
                vo.setUserId(record.getUserId());
                vo.setDisplayName(record.getDisplayName());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public MyBestScoreVO getMyBest(Long userId, String gameType) {
        if (!SUPPORTED_GAMES.contains(gameType == null ? "" : gameType.trim())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的游戏类型");
        }
        gameType = gameType.trim();
        MyBestScoreVO vo = new MyBestScoreVO();
        GameScore best = selectBestRecord(userId, gameType);
        if (best == null) {
            vo.setBestScore(0);
            vo.setBestLevel(1);
            vo.setRank(null);
            vo.setPlayCount(0);
            return vo;
        }
        vo.setBestScore(best.getScore());
        vo.setBestLevel(best.getLevel());
        vo.setRank(best.getScore() > 0 ? gameScoreMapper.selectRankByScore(gameType, best.getScore()) : null);
        vo.setPlayCount(gameScoreMapper.countByUserAndGame(gameType, userId));
        return vo;
    }

    @Override
    public GameAliasVO getMyAlias(Long userId, String gameType) {
        String normalizedGame = requireSupportedGame(gameType);
        GamePlayerAlias aliasRecord = selectAlias(userId, normalizedGame);
        GameAliasVO vo = new GameAliasVO();
        vo.setGameType(normalizedGame);
        vo.setAlias(aliasRecord == null ? null : aliasRecord.getAlias());
        return vo;
    }

    @Override
    public GameAliasVO saveMyAlias(Long userId, SaveAliasRequest req) {
        String normalizedGame = requireSupportedGame(req == null ? null : req.getGameType());
        String alias = req.getAlias() == null ? "" : req.getAlias().trim();
        if (!ALIAS_PATTERN.matcher(alias).matches()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "匿名代号需为2-12位中文、英文、数字或下划线");
        }
        // 用户×游戏唯一：已存在则更新代号（更新后只对之后提交的新成绩生效，历史成绩保留旧快照）
        GamePlayerAlias existing = selectAlias(userId, normalizedGame);
        if (existing == null) {
            GamePlayerAlias record = new GamePlayerAlias();
            record.setUserId(userId);
            record.setGameType(normalizedGame);
            record.setAlias(alias);
            gamePlayerAliasMapper.insert(record);
        } else if (!alias.equals(existing.getAlias())) {
            existing.setAlias(alias);
            gamePlayerAliasMapper.updateById(existing);
            log.info("[小游戏] 匿名代号更新: userId={}, game={}", userId, normalizedGame);
        }
        GameAliasVO vo = new GameAliasVO();
        vo.setGameType(normalizedGame);
        vo.setAlias(alias);
        return vo;
    }

    /**
     * 查询某用户在某游戏下的匿名代号设置，未设置返回 null
     */
    private GamePlayerAlias selectAlias(Long userId, String gameType) {
        return gamePlayerAliasMapper.selectOne(new LambdaQueryWrapper<GamePlayerAlias>()
                .eq(GamePlayerAlias::getUserId, userId)
                .eq(GamePlayerAlias::getGameType, gameType)
                .last("LIMIT 1"));
    }

    /**
     * 校验并归一化游戏类型参数
     */
    private String requireSupportedGame(String gameType) {
        String normalized = gameType == null ? "" : gameType.trim();
        if (!SUPPORTED_GAMES.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的游戏类型");
        }
        return normalized;
    }

    /**
     * 查询某用户在某游戏上的最高分那一局（同分取最新一条），无记录返回 null
     */
    private GameScore selectBestRecord(Long userId, String gameType) {
        return gameScoreMapper.selectOne(new LambdaQueryWrapper<GameScore>()
                .eq(GameScore::getUserId, userId)
                .eq(GameScore::getGameType, gameType)
                .orderByDesc(GameScore::getScore)
                .orderByDesc(GameScore::getId)
                .last("LIMIT 1"));
    }

    /**
     * 参数归一化：null 或越界时收敛到合法区间
     */
    private int normalize(Integer value, int min, int max) {
        if (value == null) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }
}

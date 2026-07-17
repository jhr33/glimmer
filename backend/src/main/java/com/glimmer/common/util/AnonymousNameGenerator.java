package com.glimmer.common.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 匿名昵称生成器（见开发文档 §2.1.5）
 * 格式：{中文形容词}+{中文名词}+{4位编号}
 * 示例：沉静的旅人0023、温柔的夜行者0456
 * 编号：用户ID后4位补零 + 随机2位数
 */
public class AnonymousNameGenerator {

    /** 形容词池（30个） */
    private static final String[] ADJECTIVES = {
            "沉静", "温柔", "孤独", "明亮", "寂静", "清澈", "深邃", "温暖", "柔软", "安静",
            "淡然", "轻盈", "缱绻", "皎洁", "空灵", "朦胧", "明媚", "慵懒", "恬静", "澄澈",
            "皎白", "飘渺", "慵倦", "暮色", "晨曦", "月华", "星辉", "萤光", "流光", "霁色"
    };

    /** 名词池（30个） */
    private static final String[] NOUNS = {
            "旅人", "夜行者", "观星者", "拾光者", "漫步者", "守夜人", "看云者", "听雨人", "乘风者", "追光者",
            "寻梦人", "流浪者", "行吟者", "采薇人", "听涛者", "观潮人", "望月者", "摘星人", "抚琴人", "执笔者",
            "问路人", "待雪者", "闻笛人", "看花者", "守烛人", "临渊者", "品茗人", "揽星者", "抚月人", "听风者"
    };

    /**
     * 根据用户ID生成匿名昵称
     * 编号 = 用户ID后4位补零（前4位） + 随机2位数（后2位），共6位但任务要求4位编号，
     * 此处按任务说明：用户ID后4位补零 + 随机2位数，最终取后4位显示。
     * 实现为：用户ID后4位补零 + 随机2位拼接后取后4位，保证基本唯一性。
     *
     * @param userId 用户ID
     * @return 匿名昵称，如 "沉静的旅人0023"
     */
    public static String generate(Long userId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[random.nextInt(NOUNS.length)];
        // 用户ID后4位补零
        long idPart = userId == null ? 0 : userId;
        String idSuffix = String.format("%04d", idPart % 10000);
        // 随机2位数
        int randomPart = random.nextInt(100);
        // 拼接成4位编号：取ID后2位 + 随机2位，保证包含用户信息又有随机性
        String number = idSuffix.substring(2) + String.format("%02d", randomPart);
        return adjective + "的" + noun + number;
    }
}

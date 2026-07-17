package com.glimmer.service.util;

/**
 * 花园亮度等级计算工具（见开发文档 §2.7.2）
 * 阈值 [0, 10, 30, 60, 100, 200]，等级 0-5
 */
public final class GardenBrightnessHelper {

    /** 亮度阈值（升序） */
    private static final int[] THRESHOLDS = {10, 30, 60, 100, 200};

    private GardenBrightnessHelper() {
    }

    /**
     * 根据累计萤火值计算亮度等级
     *
     * @param totalFirefly 累计萤火值
     * @return 亮度等级 0-5
     */
    public static int calculateLevel(int totalFirefly) {
        int level = 0;
        for (int threshold : THRESHOLDS) {
            if (totalFirefly >= threshold) {
                level++;
            } else {
                break;
            }
        }
        return level;
    }
}

package com.glimmer.service.dto;

import lombok.Data;

/**
 * 我的最佳成绩 VO
 */
@Data
public class MyBestScoreVO {

    /** 历史最高分（无记录时为 0） */
    private Integer bestScore;

    /** 最高分那局达到的难度等级 */
    private Integer bestLevel;

    /** 历史最佳名次（无记录时为 null） */
    private Integer rank;

    /** 总局数 */
    private Integer playCount;
}

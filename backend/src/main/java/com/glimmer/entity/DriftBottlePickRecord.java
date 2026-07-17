package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 漂流瓶捡瓶记录表（drift_bottle_pick_record）—— 新增表
 */
@Data
@TableName("drift_bottle_pick_record")
public class DriftBottlePickRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bottleId;

    private Long userId;

    /** 是否已打开: 0未打开/1已打开 */
    private Integer opened;

    private LocalDateTime pickedAt;
}

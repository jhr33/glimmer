package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章收藏文件夹（collect_folder）
 * 用户自定义文件夹名，一个用户可有多个文件夹
 */
@Data
@TableName("collect_folder")
public class CollectFolder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 文件夹名称（用户自定义） */
    private String name;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}

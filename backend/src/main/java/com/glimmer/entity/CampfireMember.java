package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 篝火成员表（campfire_member）
 */
@Data
@TableName("campfire_member")
public class CampfireMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long campfireId;

    private Long userId;

    /**
     * 进入篝火时选择的身份名称（nickname 或随机 anonymousName）
     */
    private String anonymousName;

    private LocalDateTime joinedAt;

    /**
     * 最后活跃时间（用户在篝火页面有任何操作时更新）
     * 超过 10 分钟无活动视为离线，自动退出
     */
    private LocalDateTime lastActiveAt;
}

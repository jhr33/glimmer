package com.glimmer.common.exception;

import lombok.Getter;

/**
 * 业务错误码定义（见开发文档 §4.16）
 * HTTP 状态码与业务错误码分离：业务错误码 4xxx 表示业务校验失败
 */
@Getter
public enum ErrorCode {

    // 通用错误
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或token失效"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "业务冲突"),
    BUSINESS_ERROR(422, "业务校验失败"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 用户/鉴权相关 4001-4002
    USERNAME_OR_PASSWORD_ERROR(4001, "用户名或密码错误"),
    USERNAME_EXISTS(4002, "用户名已存在"),

    // 代币相关 4003-4004
    TOKEN_NOT_ENOUGH(4003, "代币不足"),
    ALREADY_SIGNED_IN(4004, "今日已签到"),

    // 漂流瓶相关 4005-4008
    CANNOT_PICK_OWN_BOTTLE(4005, "不能捡自己的瓶子"),
    ALREADY_PICKED_BOTTLE(4006, "已捡过该瓶子"),
    ALREADY_REPLIED_BOTTLE(4007, "已回复过该瓶子"),
    ALREADY_THANKED(4008, "已感谢过该内容"),

    // 信件/AI/篝火/花园相关 4009-4014
    LETTER_REPLIED(4009, "信件已回复"),
    AI_CONVERSATION_CLOSED(4010, "AI会话已关闭"),
    CAMPFIRE_FULL(4011, "篝火人数已满"),
    FIREFLY_TOTAL_NOT_ENOUGH(4012, "累计萤火值不足"),
    FIREFLY_BALANCE_NOT_ENOUGH(4013, "萤火余额不足"),
    ALREADY_WATERED_TODAY(4014, "今日已浇水"),

    // 举报/花种相关 4015-4018
    USER_BANNED(4015, "用户已被封禁"),
    CANNOT_REPORT_SELF(4016, "不能举报自己"),
    ALREADY_REPORTED(4017, "已举报过该目标"),
    FLOWER_TYPE_UNAVAILABLE(4018, "花种未上架"),
    USER_MUTED(4019, "用户已被禁言");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

create table ai_conversation
(
    id                bigint auto_increment comment '主键'
        primary key,
    user_id           bigint                                not null comment '用户ID',
    status            varchar(20) default 'active'          not null comment '状态: active活跃/closed用户关闭/timeout超时关闭',
    message_count     int         default 0                 not null comment '当前消息数（用户+AI合计）',
    max_messages      int         default 100               not null comment '消息上限（50轮对话=100条消息）',
    started_at        datetime    default CURRENT_TIMESTAMP not null comment '开始时间',
    last_active_at    datetime    default CURRENT_TIMESTAMP not null comment '最后活跃时间（用于判断3小时超时）',
    conversation_type varchar(10) default 'paid'            not null comment '类型: free免费/paid付费',
    quota_used        int         default 0                 not null comment '已用轮次',
    quota_limit       int         default 10                not null comment '轮次上限',
    quota_reset_date  date                                  null comment '配额重置日期(仅free)',
    summary           text                                  null comment '会话摘要',
    title             varchar(50)                           null comment '会话标题(首条消息摘要)'
)
    comment 'AI对话表' charset = utf8mb4;

create index idx_user_id
    on ai_conversation (user_id);

create table ai_message
(
    id              bigint auto_increment comment '主键'
        primary key,
    conversation_id bigint                             not null comment '对话ID',
    role            varchar(10)                        not null comment '角色: user用户/ai助手',
    content         text                               not null comment '消息内容',
    created_at      datetime default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment 'AI消息表' charset = utf8mb4;

create index idx_conversation_id
    on ai_message (conversation_id);

create table announcement
(
    id           bigint auto_increment comment '主键'
        primary key,
    title        varchar(100)                          not null comment '公告标题',
    content      text                                  not null comment '公告内容',
    publisher_id bigint                                not null comment '发布管理员ID',
    status       varchar(20) default 'published'       not null comment '状态: published发布/taken_down下架',
    created_at   datetime    default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment '公共公告表' charset = utf8mb4;

create table campfire
(
    id             bigint auto_increment comment '主键'
        primary key,
    name           varchar(50)                           not null comment '篝火名称',
    type           varchar(20)                           not null comment '类型: default系统默认/custom用户创建',
    max_members    int         default 30                not null comment '人数上限: 10/20/30',
    creator_id     bigint                                not null comment '创建者ID',
    status         varchar(20) default 'active'          not null comment '状态: active活跃',
    created_at     datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    last_active_at datetime    default CURRENT_TIMESTAMP not null comment '最后活跃时间'
)
    comment '篝火表' charset = utf8mb4;

create table campfire_member
(
    id             bigint auto_increment comment '主键'
        primary key,
    campfire_id    bigint                             not null comment '篝火ID',
    user_id        bigint                             not null comment '用户ID',
    joined_at      datetime default CURRENT_TIMESTAMP not null comment '加入时间',
    last_active_at datetime                           null comment '最后活跃时间（超过10分钟无活动视为离线，自动退出）',
    anonymous_name varchar(100)                       null comment '篝火内身份名称',
    constraint uk_campfire_user
        unique (campfire_id, user_id)
)
    comment '篝火成员表' charset = utf8mb4;

create index idx_campfire_id
    on campfire_member (campfire_id);

create index idx_user_id
    on campfire_member (user_id);

create table campfire_message
(
    id             bigint auto_increment comment '主键'
        primary key,
    campfire_id    bigint                             not null comment '篝火ID',
    user_id        bigint                             not null comment '发送者ID',
    anonymous_name varchar(50)                        not null comment '发送者匿名昵称',
    content        text                               not null comment '消息内容',
    created_at     datetime default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment '篝火消息表' charset = utf8mb4;

create index idx_campfire_id
    on campfire_message (campfire_id);

create table drift_bottle
(
    id          bigint auto_increment comment '主键'
        primary key,
    user_id     bigint                                not null comment '投放者用户ID',
    content     text                                  not null comment '漂流瓶内容',
    status      varchar(20) default 'drifting'        not null comment '状态: drifting漂流中/sunk沉底',
    thanked_by  json                                  null comment '感谢者用户ID列表，JSON数组如[1,3,5]（多人可感谢，每人限一次）',
    created_at  datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    sunk_at     datetime                              null comment '沉底时间',
    hide_reason varchar(32)                           null comment '沉底原因：user=用户主动沉底 / auto_report=被多次举报自动隐藏'
)
    comment '漂流瓶表' charset = utf8mb4;

create index idx_status
    on drift_bottle (status);

create index idx_user_id
    on drift_bottle (user_id);

create table drift_bottle_pick_record
(
    id        bigint auto_increment comment '主键'
        primary key,
    bottle_id bigint                             not null comment '漂流瓶ID',
    user_id   bigint                             not null comment '捡瓶者用户ID',
    opened    tinyint  default 0                 not null comment '是否已打开: 0未打开/1已打开',
    picked_at datetime default CURRENT_TIMESTAMP not null comment '捡到时间',
    constraint uk_bottle_user
        unique (bottle_id, user_id)
)
    comment '漂流瓶捡瓶记录表' charset = utf8mb4;

create index idx_user_id
    on drift_bottle_pick_record (user_id);

create table drift_bottle_reply
(
    id         bigint auto_increment comment '主键'
        primary key,
    bottle_id  bigint                             not null comment '漂流瓶ID',
    user_id    bigint                             not null comment '回复者用户ID',
    content    text                               not null comment '回复内容',
    thanked_by json                               null comment '感谢者用户ID列表（仅瓶主可感谢）',
    created_at datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint uk_bottle_user
        unique (bottle_id, user_id)
)
    comment '漂流瓶回复表' charset = utf8mb4;

create table feedback
(
    id             bigint auto_increment comment '主键'
        primary key,
    user_id        bigint                                not null comment '提交者ID',
    content        text                                  not null comment '意见内容',
    reply          text                                  null comment '管理员回复内容',
    status         varchar(20) default 'pending'         not null comment '状态: pending待回复/replied已回复',
    reply_admin_id bigint                                null comment '回复管理员ID',
    replied_at     datetime                              null comment '回复时间',
    created_at     datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    type           varchar(20) default 'feedback'        not null comment '类型: feedback(意见反馈)/appeal(申诉)',
    report_id      bigint                                null comment '关联举报ID（申诉时必填，兼容旧数据）',
    punishment_id  bigint                                null comment '关联处罚单ID（申诉时使用，用于精准撤销）'
)
    comment '意见信/反馈表' charset = utf8mb4;

create index idx_user_id
    on feedback (user_id);

create table flower
(
    id                bigint auto_increment comment '主键'
        primary key,
    user_id           bigint                                not null comment '所属用户ID',
    flower_type_id    bigint                                not null comment '花种ID',
    stage             varchar(10) default 'seed'            not null comment '当前阶段: seed种子/sprout幼苗/seedling中苗/bud花苞/bloom开放',
    stage_water_count int         default 0                 not null comment '当前阶段已浇水次数（进入新阶段归零）',
    planted_at        datetime    default CURRENT_TIMESTAMP not null comment '种植时间',
    last_water_at     datetime                              null comment '上次浇水时间',
    bloomed_at        datetime                              null comment '开花时间'
)
    comment '花朵表' charset = utf8mb4;

create index idx_user_id
    on flower (user_id);

create table flower_type
(
    id                 bigint auto_increment comment '主键'
        primary key,
    name               varchar(50)                        not null comment '花种名称',
    description        varchar(200)                       null comment '花种描述',
    seed_to_sprout     int                                not null comment '种子→幼苗所需浇水次数',
    sprout_to_seedling int                                not null comment '幼苗→中苗所需浇水次数',
    seedling_to_bud    int                                not null comment '中苗→花苞所需浇水次数',
    bud_to_bloom       int                                not null comment '花苞→开放所需浇水次数',
    icon_seed          varchar(255)                       null comment '种子阶段图标URL',
    icon_sprout        varchar(255)                       null comment '幼苗阶段图标URL',
    icon_seedling      varchar(255)                       null comment '中苗阶段图标URL',
    icon_bud           varchar(255)                       null comment '花苞阶段图标URL',
    icon_bloom         varchar(255)                       null comment '开放阶段图标URL',
    redeem_firefly     int                                not null comment '兑换所需萤火余额',
    required_firefly   int      default 0                 not null comment '解锁所需累计萤火值',
    available          tinyint  default 1                 not null comment '是否上架: 0否/1是',
    created_at         datetime default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment '花种配置表' charset = utf8mb4;

create table letter
(
    id          bigint auto_increment comment '主键'
        primary key,
    sender_id   bigint                                not null comment '发信人ID',
    receiver_id bigint                                not null comment '收信人ID',
    parent_id   bigint                                null comment '父信件ID（首次写信为空，回复时指向上一封）',
    source_type varchar(20) default 'direct'          null comment '来源类型: direct直接写信/bottle_reply漂流瓶回复延伸',
    source_id   bigint                                null comment '来源ID（漂流瓶回复ID，source_type=bottle_reply时有值）',
    content     text                                  not null comment '信件内容',
    is_replied  tinyint     default 0                 not null comment '是否已被回复（0未回复/1已回复，对方回复后不可再发）',
    is_read     tinyint     default 0                 not null comment '是否已读: 0未读/1已读',
    thanked_by  json                                  null comment '感谢者用户ID列表',
    created_at  datetime    default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment '信件表' charset = utf8mb4;

create index idx_receiver_id
    on letter (receiver_id);

create index idx_sender_id
    on letter (sender_id);

create table notification
(
    id         bigint auto_increment comment '主键'
        primary key,
    user_id    bigint                             not null comment '接收者用户ID',
    type       varchar(30)                        not null comment '类型: report_result举报结果/feedback_reply意见反馈回复/announcement公告/system系统通知',
    title      varchar(100)                       not null comment '通知标题',
    content    text                               not null comment '通知内容',
    ref_type   varchar(30)                        null comment '关联业务类型: report/feedback/announcement',
    ref_id     bigint                             null comment '关联业务ID',
    is_read    tinyint  default 0                 not null comment '是否已读: 0未读/1已读',
    created_at datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    extra      text                               null comment '额外信息（JSON格式），如举报审核结果、处罚类型等'
)
    comment '通知表' charset = utf8mb4;

create index idx_user_read
    on notification (user_id, is_read);

create table punishment
(
    id          bigint auto_increment comment '处罚单ID'
        primary key,
    user_id     bigint                                not null comment '被处罚用户ID',
    type        varchar(20)                           not null comment '处罚类型: WARNING/MUTE_24H/MUTE_7D/BAN',
    reason      varchar(500)                          not null comment '处罚原因',
    source_type varchar(20)                           not null comment '来源: ADMIN(管理员手动)/REPORT(举报通过)/AUTO(系统自动)',
    source_id   bigint                                null comment '来源ID（如report_id或admin_operation_id）',
    start_at    datetime    default CURRENT_TIMESTAMP not null comment '处罚开始时间',
    end_at      datetime                              null comment '处罚结束时间（永久封禁为NULL）',
    status      varchar(20) default 'ACTIVE'          not null comment '状态: ACTIVE生效/REVOKED已撤销/EXPIRED已过期',
    created_at  datetime    default CURRENT_TIMESTAMP not null,
    updated_at  datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
)
    comment '处罚单表';

create index idx_end_at
    on punishment (end_at);

create index idx_user_status
    on punishment (user_id, status);

create table report
(
    id             bigint auto_increment comment '主键'
        primary key,
    reporter_id    bigint                                not null comment '举报人ID',
    target_user_id bigint                                not null comment '被举报人ID',
    target_type    varchar(30)                           not null comment '目标类型: drift_bottle/bottle_reply/letter/campfire_message',
    target_id      bigint                                not null comment '目标ID',
    content        varchar(500)                          not null comment '举报原因',
    status         varchar(20) default 'pending'         not null comment '状态: pending待审核/reviewed已审核',
    result         varchar(20)                           null comment '审核结果: approved举报成立/rejected举报驳回',
    reviewer_id    bigint                                null comment '审核管理员ID',
    review_comment varchar(500)                          null comment '审核意见',
    reviewed_at    datetime                              null comment '处理时间',
    punishment_id  bigint                                null comment '关联处罚单ID',
    created_at     datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    appeal_count   int         default 0                 not null comment '申诉次数（最多3次）',
    constraint uk_reporter_target
        unique (reporter_id, target_type, target_id)
)
    comment '举报记录表' charset = utf8mb4;

create table sign_in_record
(
    id         bigint auto_increment comment '主键'
        primary key,
    user_id    bigint                             not null comment '用户ID',
    sign_date  date                               not null comment '签到日期',
    created_at datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint uk_user_date
        unique (user_id, sign_date)
)
    comment '签到记录表' charset = utf8mb4;

create table token_transaction
(
    id         bigint auto_increment comment '主键'
        primary key,
    user_id    bigint                             not null comment '用户ID',
    type       varchar(10)                        not null comment '类型: earn收入/spend支出',
    amount     int                                not null comment '金额',
    source     varchar(50)                        not null comment '来源: sign_in签到/receive_thanks收到感谢/write_letter写信/create_campfire创建篝火/ai_chatAI对话',
    ref_id     bigint                             null comment '关联业务ID（如签到记录ID、感谢关联的信件ID等）',
    created_at datetime default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment '代币流水表' charset = utf8mb4;

create index idx_user_id
    on token_transaction (user_id);

create table user
(
    id                   bigint auto_increment comment '主键'
        primary key,
    username             varchar(50)                           not null comment '用户名',
    password             varchar(100)                          not null comment '密码(BCrypt加密)',
    password_changed_at  datetime                              null comment '最近一次修改密码时间，用于限制修改频率（一天一次）',
    nickname             varchar(50)                           null comment '昵称',
    anonymous_name       varchar(50)                           null comment '匿名昵称，系统自动生成，漂流瓶/篝火中使用',
    role                 varchar(10) default 'user'            not null comment '角色: user普通用户/admin管理员',
    status               varchar(10) default 'active'          not null comment '状态: active正常/banned封禁',
    token_balance        int         default 0                 not null comment '代币余额（签到获得/收到感谢获得）',
    total_firefly        int         default 0                 not null comment '累计萤火值（只增不减，决定花园亮度）',
    firefly_balance      int         default 0                 not null comment '萤火余额（兑换花种花肥消耗，可消费）',
    total_sign_days      int         default 0                 not null comment '累计签到天数',
    pending_report_count int         default 0                 not null comment '待处理举报数（>=7时自动封禁发言）',
    version              int         default 0                 not null comment '乐观锁版本号（防并发）',
    created_at           datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    updated_at           datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    ai_context           json                                  null comment 'AI记忆关键信息JSON',
    constraint uk_username
        unique (username)
)
    comment '用户表' charset = utf8mb4;



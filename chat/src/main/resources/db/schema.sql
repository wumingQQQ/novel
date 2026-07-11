create table if not exists chat_sessions(
    id bigint primary key auto_increment comment '聊天会话主键',
    user_id bigint comment '用户主键',
    character_id bigint not null comment '角色主键',
    user_role_version_id bigint comment '用户个人角色版本主键，空表示公共角色基线',
    status varchar(20) not null default 'ACTIVE' comment '会话状态',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    key idx_chat_sessions_user_id (user_id),
    key idx_chat_sessions_character_id (character_id),
    key idx_chat_sessions_user_role_version_id (user_role_version_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists chat_messages(
    id bigint primary key auto_increment comment '聊天消息主键',
    session_id bigint not null comment '聊天会话主键',
    role varchar(20) not null comment '消息角色',
    content text not null comment '消息内容',
    create_time datetime default current_timestamp comment '创建时间',
    key idx_chat_messages_session_id (session_id, id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

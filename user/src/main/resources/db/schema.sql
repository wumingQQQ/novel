create table if not exists users(
    id bigint primary key auto_increment comment '用户主键',
    username varchar(50) not null comment '用户名',
    nickname varchar(50) comment '用户昵称',
    email varchar(100) not null comment '邮箱',
    password_hash varchar(100) not null comment '密码哈希',
    status varchar(20) not null default 'ACTIVE' comment '用户状态',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    unique key uk_users_username (username)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists user_refresh_tokens(
    id bigint primary key auto_increment comment '刷新令牌主键',
    user_id bigint not null comment '用户主键',
    token_hash varchar(128) not null comment '刷新令牌哈希',
    expires_time datetime not null comment '过期时间',
    revoked_time datetime comment '吊销时间',
    last_used_time datetime comment '最近使用时间',
    create_time datetime default current_timestamp comment '创建时间',
    unique key uk_user_refresh_tokens_hash (token_hash),
    key idx_user_refresh_tokens_user_id (user_id),
    key idx_user_refresh_tokens_expires_time (expires_time)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists users(
    id bigint primary key auto_increment comment '用户主键',
    username varchar(50) not null comment '用户名',
    nickname varchar(50) comment '用户昵称',
    status varchar(20) not null default 'ACTIVE' comment '用户状态',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    unique key uk_users_username (username)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

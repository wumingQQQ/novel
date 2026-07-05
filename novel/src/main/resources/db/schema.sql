create table if not exists novels(
    id bigint primary key auto_increment comment '小说主键',
    name varchar(50) not null comment '小说名称',
    file_path varchar(100) not null comment '小说保存路径',
    uploader_id bigint not null comment '上传者id',
    create_time datetime default current_timestamp comment '创建时间',
    key idx_novels_uploader_id(uploader_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists chapters(
    id bigint primary key auto_increment comment '章节主键',
    novel_id bigint not null comment '小说主键',
    title varchar(255) not null comment '章节标题',
    sequence int not null comment '章节序号',
    content mediumtext not null comment '章节正文',
    create_time datetime default current_timestamp comment '创建时间',
    unique key uk_chapters_novel_sequence (novel_id, sequence),
    key idx_chapters_novel_id (novel_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists jobs(
    id bigint primary key auto_increment comment '任务主键',
    novel_id bigint not null comment '小说主键',
    user_id bigint not null comment '创建者id',
    protagonist_name varchar(50) not null comment '用户扮演角色',
    target_name varchar(50) not null comment 'LLM扮演角色',
    stage tinyint not null default 0 comment '任务阶段',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    key idx_jobs_novel_id (novel_id),
    key idx_jobs_user_id(user_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

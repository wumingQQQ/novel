create database if not exists novel;
use novel;

create table if not exists novels(
    id bigint primary key auto_increment comment '小说主键',
    name varchar(50) not null comment '小说名称',
    file_path varchar(100) not null comment '小说保存路径',
    create_time datetime default current_timestamp comment '创建时间'
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
    protagonist_name varchar(50) not null comment '用户扮演角色',
    target_name varchar(50) not null comment 'LLM扮演角色',
    stage tinyint not null default 0 comment '任务阶段',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    key idx_jobs_novel_id (novel_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists layers(
    id bigint primary key auto_increment comment '剧情层主键',
    layer_index int not null comment '层序号',
    layer_name varchar(50) not null comment '层名称',
    novel_id bigint not null comment '小说主键',
    start_chapter_sequence int not null comment '起始章节序号',
    end_chapter_sequence int not null comment '结束章节序号',
    create_time datetime default current_timestamp comment '创建时间',
    unique key uk_layers_novel_index (novel_id, layer_index),
    key idx_layers_novel_id (novel_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists scenes(
    id bigint primary key auto_increment comment '场景主键',
    novel_id bigint not null comment '小说主键',
    chapter_id bigint not null comment '章节主键',
    sequence int not null comment '场景序号',
    content text not null comment '场景内容',
    create_time datetime default current_timestamp comment '创建时间',
    unique key uk_scenes_chapter_sequence (chapter_id, sequence),
    key idx_scenes_novel_chapter (novel_id, chapter_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists scene_pool(
    id bigint primary key auto_increment comment '场景分池主键',
    scene_id bigint not null comment '场景主键',
    novel_id bigint not null comment '小说主键',
    job_id bigint not null comment '任务主键',
    pool_type varchar(30) not null comment '池类型',
    confidence double comment '置信度',
    create_time datetime default current_timestamp comment '创建时间',
    unique key uk_scene_pool_job_scene_type (job_id, scene_id, pool_type),
    key idx_scene_pool_job_type_confidence (job_id, pool_type, confidence),
    key idx_scene_pool_novel_job (novel_id, job_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists evidences(
    id bigint primary key auto_increment comment '证据主键',
    novel_id bigint not null comment '小说主键',
    job_id bigint not null comment '任务主键',
    layer_id bigint not null comment '剧情层主键',
    pool_type varchar(30) not null comment '池类型',
    conclusion varchar(500) comment '画像结论',
    supporting_quotes json comment '支撑原文引用',
    scene_ids json comment '引用对应场景id',
    confidence double comment '置信度',
    key idx_evidences_job_layer_pool (job_id, layer_id, pool_type),
    key idx_evidences_novel_id (novel_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists character_profiles(
    id bigint primary key auto_increment comment '角色画像主键',
    job_id bigint not null comment '任务主键',
    basic_setting json comment '角色基础设定',
    core_traits json comment '核心性格标签',
    value_system text comment '价值观与判断标准',
    behavior_patterns text comment '行为模式',
    emotional_patterns text comment '情绪模式',
    relationship_attitude text comment '关系态度',
    weaknesses text comment '弱点与矛盾点',
    speech_style json comment '说话风格',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    unique key uk_character_profiles_job_id (job_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists interaction_profiles(
    id bigint primary key auto_increment comment '互动画像主键',
    job_id bigint not null comment '任务主键',
    character_id bigint not null comment '角色画像主键',
    protagonist_name varchar(50) comment '主角名称',
    tone varchar(100) comment '互动基调',
    key_events json comment '关键事件',
    conversation_samples json comment '对话示例',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    unique key uk_interaction_profiles_job_id (job_id),
    key idx_interaction_profiles_character_id (character_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

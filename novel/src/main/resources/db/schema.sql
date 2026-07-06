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
    summary text comment '章节摘要',
    scene_boundaries text comment '场景切换段落号列表，JSON数组',
    analysis_status varchar(20) default 'PENDING' comment 'PENDING/DONE/FAILED',
    analysis_error text comment '章节分析失败原因',
    analyzed_time datetime comment '章节分析完成时间',
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

create table if not exists role_characters(
    id bigint primary key auto_increment comment '角色主键',
    novel_id bigint not null comment '小说主键',
    novel_name varchar(100) not null comment '小说名称',
    character_name varchar(50) not null comment '角色名称',
    aliases json comment '角色别名',
    build_status varchar(20) default 'PENDING' comment 'PENDING/BUILDING/COMPLETED/INCOMPLETE',
    build_error text comment '构建失败或不达标原因',
    completed_time datetime comment '构建完成时间',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    unique key uk_role_characters_novel_character (novel_id, character_name),
    key idx_role_characters_novel_id (novel_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists novel_passages(
    id bigint primary key auto_increment comment 'Passage主键',
    novel_id bigint not null comment '小说主键',
    chapter_id bigint not null comment '章节主键',
    content text not null comment '原文内容',
    sequence int not null comment '全书顺序',
    inner_sequence int not null comment '章节内顺序',
    start_paragraph int not null comment '章节内起始段落编号',
    end_paragraph int not null comment '章节内结束段落编号',
    vector_status varchar(20) default 'PENDING' comment 'PENDING/INDEXED/FAILED',
    vector_error text comment '向量索引失败原因',
    indexed_time datetime comment '向量索引完成时间',
    create_time datetime default current_timestamp comment '创建时间',
    key idx_novel_passages_novel_seq (novel_id, sequence),
    key idx_novel_passages_chapter (chapter_id, inner_sequence),
    key idx_novel_passages_vector_status (vector_status)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists passage_characters(
    passage_id bigint not null comment 'Passage主键',
    character_name varchar(50) not null comment '角色名称',
    primary key (passage_id, character_name),
    key idx_passage_characters_character_name (character_name)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists role_examples(
    id bigint primary key auto_increment comment '样本主键',
    character_id bigint not null comment '角色主键',
    character_name varchar(50) not null comment '角色名称',
    passage_id bigint not null comment '来源Passage主键',
    sample_type varchar(30) not null comment 'INTERACTION/NARRATION_EVAL',
    sample_text text not null comment '完整交互单元原文',
    confidence double comment '归因置信度',
    vector_status varchar(20) default 'PENDING' comment 'PENDING/INDEXED/FAILED',
    vector_error text comment '向量索引失败原因',
    indexed_time datetime comment '向量索引完成时间',
    create_time datetime default current_timestamp comment '创建时间',
    key idx_role_examples_character_id (character_id),
    key idx_role_examples_passage_id (passage_id),
    key idx_role_examples_type (sample_type),
    key idx_role_examples_vector_status (vector_status)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists role_reaction_rules(
    id bigint primary key auto_increment comment '规则主键',
    character_id bigint not null comment '角色主键',
    character_name varchar(50) not null comment '角色名称',
    situation varchar(100) not null comment '情境描述',
    rule text not null comment '归纳出的反应规则',
    evidence_passage_ids json comment '支撑证据的passageId列表',
    vector_status varchar(20) default 'PENDING' comment 'PENDING/INDEXED/FAILED',
    vector_error text comment '向量索引失败原因',
    indexed_time datetime comment '向量索引完成时间',
    create_time datetime default current_timestamp comment '创建时间',
    key idx_role_reaction_rules_character_id (character_id),
    key idx_role_reaction_rules_vector_status (vector_status)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists role_profiles(
    id bigint primary key auto_increment comment '画像主键',
    character_id bigint unique not null comment '角色主键',
    character_name varchar(50) comment '角色名称',
    novel_id bigint comment '小说主键',
    novel_name varchar(100) comment '小说名称',
    basic_info json comment '基础信息',
    core_traits text comment '核心性格特质',
    speaking_style json comment '说话风格',
    forbidden_behaviors text comment '绝不做的事',
    build_version varchar(20) default 'v1.0.0' comment '构建版本',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    key idx_role_profiles_character_id (character_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

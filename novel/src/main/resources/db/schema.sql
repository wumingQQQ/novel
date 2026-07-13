create table if not exists novels(
    id bigint primary key auto_increment comment '小说主键',
    name varchar(50) not null comment '小说名称',
    file_path varchar(255) not null comment '小说保存路径或对象存储key',
    storage_type varchar(20) not null default 'LOCAL' comment '文件存储类型：LOCAL/COS',
    object_key varchar(255) comment '对象存储key，LOCAL时与file_path一致',
    original_filename varchar(255) comment '上传原始文件名',
    file_size bigint comment '文件大小，单位字节',
    uploader_id bigint not null comment '上传者id',
    create_time datetime default current_timestamp comment '创建时间',
    key idx_novels_uploader_id(uploader_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists novel_preprocesses(
    id bigint primary key auto_increment comment '小说预处理状态主键',
    novel_id bigint not null comment '小说主键',
    status varchar(20) not null default 'PENDING' comment 'PENDING/RUNNING/READY/FAILED',
    current_stage varchar(30) comment '当前执行的预处理阶段',
    completed_stage varchar(30) not null default 'NONE' comment '最后成功完成的预处理阶段',
    chapter_count int not null default 0 comment '最近完成切分的章节数量',
    passage_count int not null default 0 comment '最近完成构建的Passage数量',
    failure_reason text comment '最近一次失败原因',
    started_time datetime comment '当前预处理轮次开始时间',
    completed_time datetime comment 'Passage构建完成时间',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    unique key uk_novel_preprocesses_novel_id (novel_id)
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
    status varchar(20) not null default 'PENDING' comment 'PENDING/RUNNING/DONE/FAILED',
    failure_reason text comment '最近一次失败原因',
    started_time datetime comment '最近一次开始执行时间',
    finished_time datetime comment '最近一次结束执行时间',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    key idx_jobs_novel_id (novel_id),
    key idx_jobs_user_create_time(user_id, create_time)
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

create table if not exists role_adjust_requests(
    id bigint primary key auto_increment comment '个人角色调整请求主键',
    user_id bigint not null comment '请求用户主键',
    character_id bigint not null comment '公共角色主键',
    base_version_id bigint comment '选定的个人版本基线，空表示公共角色基线',
    requirement text not null comment '用户调整要求',
    chat_text text comment '辅助理解意图的聊天上下文',
    status varchar(20) not null default 'PENDING' comment 'PENDING/GENERATING/READY/CONFIRMED/COMPLETED/FAILED/CANCELLED',
    failure_reason text comment '生成失败原因',
    created_version_id bigint comment '本请求创建的个人角色版本主键',
    cancelled_time datetime comment '取消时间',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    key idx_role_adjust_requests_user_character_time (user_id, character_id, create_time),
    key idx_role_adjust_requests_status_time (status, update_time)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists role_adjust_items(
    id bigint primary key auto_increment comment '个人角色调整候选项主键',
    request_id bigint not null comment '所属调整请求主键',
    change_type varchar(20) not null comment 'ADD/REPLACE/DISABLE',
    adjustment_id varchar(36) comment '稳定行为调整标识',
    target_adjustment_id varchar(36) comment 'REPLACE或DISABLE目标调整标识',
    applicability text comment '适用条件',
    expected_behavior text comment '应当表现',
    forbidden_behavior text comment '不应表现',
    status varchar(20) not null default 'PENDING' comment 'PENDING/ACCEPTED/REJECTED/REVISING',
    revision_feedback text comment '用户改写意见',
    revision_error text comment '单项改写失败原因',
    display_order int not null comment '请求内展示顺序',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    key idx_role_adjust_items_request_order (request_id, display_order),
    key idx_role_adjust_items_request_status (request_id, status),
    key idx_role_adjust_items_target (request_id, target_adjustment_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists role_adjust_evidences(
    id bigint primary key auto_increment comment '个人角色调整原作证据主键',
    item_id bigint not null comment '所属调整候选项主键',
    passage_ids json not null comment '原作Passage主键列表',
    create_time datetime default current_timestamp comment '创建时间',
    unique key uk_role_adjust_evidences_item (item_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists personal_role_track(
    id bigint primary key auto_increment comment '用户个人角色版本轨迹主键',
    user_id bigint not null comment '用户主键',
    character_id bigint not null comment '公共角色主键',
    latest_version_id bigint comment '最新个人版本主键',
    latest_version_no int not null default 0 comment '最新个人版本号',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    unique key uk_personal_role_track_user_character (user_id, character_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists personal_role_versions(
    id bigint primary key auto_increment comment '用户个人角色不可变版本主键',
    track_id bigint not null comment '所属个人角色版本轨迹主键',
    version_no int not null comment '轨迹内递增版本号',
    parent_version_id bigint comment '派生来源个人版本主键，空表示公共角色基线',
    source_request_id bigint not null comment '创建本版本的调整请求主键',
    behavior_adjustments_snapshot json not null comment '有效行为调整补丁快照',
    create_time datetime default current_timestamp comment '创建时间',
    unique key uk_personal_role_versions_track_no (track_id, version_no),
    key idx_personal_role_versions_parent (parent_version_id),
    key idx_personal_role_versions_source_request (source_request_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

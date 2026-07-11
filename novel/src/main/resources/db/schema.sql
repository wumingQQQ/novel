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

create table if not exists role_evaluations(
    id bigint primary key auto_increment comment '独立角色评测主键',
    user_id bigint not null comment '评测创建者id',
    character_id bigint not null comment '被评测的公共角色主键',
    user_role_track_id bigint comment '用户针对该角色的个人演进轨迹主键',
    user_role_version_id bigint comment '当前使用的个人角色版本主键',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    key idx_role_evaluations_user_time(user_id, create_time),
    key idx_role_evaluations_character_id(character_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists user_role_tracks(
    id bigint primary key auto_increment comment '用户个人角色演进轨迹主键',
    user_id bigint not null comment '所属用户id',
    character_id bigint not null comment '来源公共角色主键',
    latest_version_id bigint comment '最新个人版本主键',
    latest_version_no int not null default 0 comment '最新个人版本号',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    unique key uk_user_role_tracks_user_character(user_id, character_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists user_role_versions(
    id bigint primary key auto_increment comment '用户个人角色不可变版本主键',
    user_role_track_id bigint not null comment '所属个人角色演进轨迹主键',
    version_no int not null comment '轨迹内递增版本号',
    parent_version_id bigint comment '派生来源个人版本主键，基线首次创建时为空',
    source_evaluation_id bigint comment '触发本版本的评测主键',
    source_improvement_id bigint comment '触发本版本的规则建议主键',
    source_improvement_batch_id bigint comment '触发本版本的规则改进批次主键',
    create_time datetime default current_timestamp comment '创建时间',
    unique key uk_user_role_versions_track_no(user_role_track_id, version_no),
    key idx_user_role_versions_parent(parent_version_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists user_role_profiles(
    id bigint primary key auto_increment comment '用户画像版本快照主键',
    user_role_version_id bigint not null comment '个人角色版本主键',
    basic_info json comment '基础信息版本快照',
    core_traits text comment '核心性格版本快照',
    speaking_style json comment '说话风格版本快照',
    forbidden_behaviors text comment '行为禁忌版本快照',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    unique key uk_user_role_profiles_version(user_role_version_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists user_role_reaction_rules(
    id bigint primary key auto_increment comment '用户反应规则覆写主键',
    user_role_version_id bigint not null comment '个人角色版本主键',
    source_rule_id bigint comment '来源公共规则主键，用户新增规则时为空',
    situation varchar(100) not null comment '情境描述',
    rule text not null comment '个人反应规则',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    unique key uk_user_role_rules_version_source(user_role_version_id, source_rule_id),
    key idx_user_role_rules_version(user_role_version_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists role_evaluation_cases(
    id bigint primary key auto_increment comment '评测案例主键',
    evaluation_id bigint not null comment '所属独立评测主键',
    dataset_version varchar(50) not null comment '数据集版本',
    character_id bigint not null comment '被评测的公共角色主键快照',
    passage_id bigint not null comment '评测时遮蔽的来源Passage主键',
    source_example_ids json not null comment '该Passage对应的角色样本主键列表',
    test_input text not null comment '固定测试输入',
    source_passage text not null comment '隐藏的原作Passage',
    expected_behaviors text comment '预期角色行为约束',
    scoring_rubric text comment '评分要点',
    difficulty varchar(20) comment '难度标签',
    status varchar(20) not null default 'DRAFT' comment 'DRAFT/APPROVED/REJECTED',
    reviewed_time datetime comment '审核时间',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    key idx_role_evaluation_cases_evaluation_dataset (evaluation_id, dataset_version),
    key idx_role_evaluation_cases_status (status)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists role_evaluation_runs(
    id bigint primary key auto_increment comment '评测运行主键',
    evaluation_id bigint not null comment '所属独立评测主键',
    case_id bigint not null comment '评测案例主键',
    user_role_version_id bigint comment '本次运行实际使用的个人版本主键',
    status varchar(20) not null default 'PENDING' comment 'PENDING/RUNNING/SUCCEEDED/INVALID/FAILED',
    config_snapshot json comment '模型、提示词和检索配置快照',
    retrieved_documents json comment '实际召回的文档与元数据',
    response_content text comment '角色生成回复',
    generation_cost_ms bigint comment '角色回复耗时毫秒',
    judge_result json comment 'Judge分维度评分与引用',
    total_score double comment 'Judge总分',
    judge_reason text comment 'Judge综合理由',
    failure_reason text comment '失败或无效原因',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    key idx_role_evaluation_runs_evaluation_time (evaluation_id, create_time),
    key idx_role_evaluation_runs_case_time (case_id, create_time),
    key idx_role_evaluation_runs_status (status)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists role_evaluation_rule_improvements(
    id bigint primary key auto_increment comment '评测规则改进建议主键',
    evaluation_id bigint not null comment '所属独立评测主键',
    batch_id bigint comment '所属规则改进批次主键，旧单运行建议为空',
    run_id bigint not null comment '来源评测运行主键',
    character_id bigint not null comment '被评测的公共角色主键快照',
    rule_id bigint not null comment '待覆写的来源公共反应规则主键',
    situation varchar(100) not null comment '对应情境',
    original_rule text not null comment '生成建议时的原规则',
    proposed_rule text not null comment '待审核新规则',
    rationale text not null comment '基于评测反馈的改进理由',
    status varchar(20) not null default 'DRAFT' comment 'DRAFT/APPLIED/REJECTED',
    base_user_role_version_id bigint comment '应用建议时选定的历史个人版本主键',
    user_role_version_id bigint comment '应用后使用的个人角色版本主键',
    user_role_reaction_rule_id bigint comment '应用后生成或更新的个人规则主键',
    reviewed_time datetime comment '审核时间',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    key idx_role_evaluation_rule_improvements_evaluation (evaluation_id),
    key idx_role_evaluation_rule_improvements_batch (batch_id),
    key idx_role_evaluation_rule_improvements_run (run_id),
    key idx_role_evaluation_rule_improvements_rule (rule_id, status)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists role_evaluation_improvement_batches(
    id bigint primary key auto_increment comment '评测规则改进批次主键',
    evaluation_id bigint not null comment '所属独立评测主键',
    user_role_version_id bigint comment '批次运行使用的个人角色版本主键，公共基线时为空',
    run_count int not null comment '汇总运行数量',
    max_changes int not null comment '本批次最大规则修改数量',
    summary text comment 'LLM对整组运行的汇总结论',
    status varchar(20) not null default 'DRAFT' comment 'DRAFT/APPLIED/REJECTED',
    reviewed_time datetime comment '批次审核时间',
    create_time datetime default current_timestamp comment '创建时间',
    update_time datetime default current_timestamp on update current_timestamp comment '更新时间',
    key idx_role_evaluation_improvement_batches_evaluation (evaluation_id),
    key idx_role_evaluation_improvement_batches_version (user_role_version_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table if not exists role_evaluation_rule_improvement_runs(
    id bigint primary key auto_increment comment '规则建议运行证据关联主键',
    improvement_id bigint not null comment '规则改进建议主键',
    run_id bigint not null comment '支撑评测运行主键',
    create_time datetime default current_timestamp comment '创建时间',
    unique key uk_role_evaluation_improvement_run (improvement_id, run_id),
    key idx_role_evaluation_rule_improvement_runs_run (run_id)
) engine = InnoDB charset = utf8mb4 collate = utf8mb4_unicode_ci;

ALTER TABLE chapters
    ADD COLUMN summary TEXT COMMENT '章节摘要',
    ADD COLUMN scene_boundaries TEXT COMMENT '场景切换段落号列表，JSON数组，如[10,23,45]',
    ADD COLUMN analysis_status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/DONE/FAILED',
    ADD COLUMN analysis_error TEXT COMMENT '章节分析失败原因',
    ADD COLUMN analyzed_time DATETIME COMMENT '章节分析完成时间';

CREATE TABLE IF NOT EXISTS role_characters (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    novel_name VARCHAR(100) NOT NULL,
    character_name VARCHAR(50) NOT NULL,
    aliases JSON COMMENT '角色别名，初版可为空',
    build_status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/BUILDING/COMPLETED/INCOMPLETE',
    build_error TEXT COMMENT '构建失败或不达标原因',
    completed_time DATETIME COMMENT '构建完成时间',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    UNIQUE KEY uk_novel_character (novel_id, character_name),
    INDEX idx_novel (novel_id)
) COMMENT '角色唯一标识表';

CREATE TABLE IF NOT EXISTS novel_passages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    content TEXT NOT NULL COMMENT '原文内容',
    sequence INT NOT NULL COMMENT '全书顺序',
    chapter_sequence INT NOT NULL COMMENT '章节内顺序',
    main_characters TEXT COMMENT 'Passage出场人物，JSON数组',
    word_count INT NOT NULL,
    start_paragraph INT NOT NULL COMMENT '对应章节内起始段落编号',
    end_paragraph INT NOT NULL COMMENT '对应章节内结束段落编号',
    vector_status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/INDEXED/FAILED',
    vector_error TEXT,
    indexed_time DATETIME,
    create_time DATETIME NOT NULL,
    INDEX idx_novel_seq (novel_id, sequence),
    INDEX idx_chapter (chapter_id, chapter_sequence),
    INDEX idx_vector_status (vector_status)
) COMMENT '小说文本块';

CREATE TABLE IF NOT EXISTS passage_characters (
    passage_id BIGINT NOT NULL,
    character_name VARCHAR(50) NOT NULL,
    PRIMARY KEY (passage_id, character_name),
    INDEX idx_character_name (character_name)
) COMMENT 'passage 与角色名映射，用于候选 passage 筛选';

CREATE TABLE IF NOT EXISTS role_examples (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    character_name VARCHAR(50) NOT NULL,
    passage_id BIGINT NOT NULL,
    sample_type VARCHAR(30) NOT NULL COMMENT 'INTERACTION/NARRATION_EVAL',
    sample_text TEXT NOT NULL COMMENT '完整交互单元原文，直接用于向量化和 few-shot 注入',
    confidence DOUBLE COMMENT '0.0-1.0',
    vector_status VARCHAR(20) DEFAULT 'PENDING',
    vector_error TEXT,
    indexed_time DATETIME,
    create_time DATETIME NOT NULL,
    INDEX idx_character (character_id),
    INDEX idx_passage (passage_id),
    INDEX idx_type (sample_type),
    INDEX idx_vector_status (vector_status)
) COMMENT '角色交互单元样本库';

CREATE TABLE IF NOT EXISTS role_reaction_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    character_name VARCHAR(50) NOT NULL,
    situation VARCHAR(100) NOT NULL COMMENT '情境描述，如：被质疑时、被问及感情时',
    rule TEXT NOT NULL COMMENT '归纳出的反应规则',
    evidence_passage_ids JSON COMMENT '支撑证据的passageId列表',
    vector_status VARCHAR(20) DEFAULT 'PENDING',
    vector_error TEXT,
    indexed_time DATETIME,
    create_time DATETIME NOT NULL,
    INDEX idx_character (character_id),
    INDEX idx_vector_status (vector_status)
) COMMENT '角色情境反应规则';

CREATE TABLE IF NOT EXISTS role_profiles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT UNIQUE NOT NULL,
    character_name VARCHAR(50),
    novel_id BIGINT,
    novel_name VARCHAR(100),
    basic_info JSON COMMENT '基础信息：age, gender, occupation, appearance',
    core_traits TEXT COMMENT '3-5个核心性格特质，含能力设定',
    speaking_style JSON COMMENT '说话风格：signature + distinctivePatterns + avoidPatterns',
    forbidden_behaviors TEXT COMMENT '绝不做的事，换行分隔',
    build_version VARCHAR(20) DEFAULT 'v1.0.0',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    INDEX idx_character (character_id)
) COMMENT '角色画像摘要';

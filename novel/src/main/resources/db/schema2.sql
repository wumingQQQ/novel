CREATE TABLE IF NOT EXISTS role_characters (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    novel_name VARCHAR(100) NOT NULL,
    character_name VARCHAR(50) NOT NULL,
    aliases JSON COMMENT '角色别名，初版可为空',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    UNIQUE KEY uk_novel_character (novel_id, character_name),
    INDEX idx_novel (novel_id)
) COMMENT '角色唯一标识表';

CREATE TABLE IF NOT EXISTS novel_passages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    novel_id BIGINT NOT NULL,
    chapter_id BIGINT,
    content TEXT NOT NULL COMMENT '原文内容',
    sequence INT NOT NULL COMMENT '全书顺序',
    word_count INT NOT NULL,
    vector_status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/INDEXED/FAILED',
    vector_error TEXT,
    indexed_time DATETIME,
    create_time DATETIME NOT NULL,
    INDEX idx_novel_seq (novel_id, sequence),
    INDEX idx_vector_status (vector_status)
) COMMENT '小说文本块';

CREATE TABLE IF NOT EXISTS role_examples (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT NOT NULL,
    character_name VARCHAR(50) NOT NULL,
    passage_id BIGINT NOT NULL,
    sample_type VARCHAR(30) NOT NULL COMMENT 'DIALOGUE/CHARACTER_DESCRIPTION',
    sample_text TEXT NOT NULL COMMENT '完整样本文本',
    dialogue_text TEXT COMMENT '对话原文，仅DIALOGUE类型使用',
    context_before TEXT COMMENT '前文',
    context_after TEXT COMMENT '后文',
    confidence DOUBLE COMMENT '0.0-1.0',
    extract_method VARCHAR(20) COMMENT 'RULE/LLM/RULE_LLM',
    emotional_tag VARCHAR(30) COMMENT '初版可为空',
    situation_tag VARCHAR(50) COMMENT '初版可为空',
    vector_status VARCHAR(20) DEFAULT 'PENDING',
    vector_error TEXT,
    indexed_time DATETIME,
    create_time DATETIME NOT NULL,
    INDEX idx_character (character_id),
    INDEX idx_passage (passage_id),
    INDEX idx_type (sample_type),
    INDEX idx_vector_status (vector_status)
) COMMENT '角色样本库';

CREATE TABLE IF NOT EXISTS role_profiles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    character_id BIGINT UNIQUE NOT NULL,
    character_name VARCHAR(50),
    novel_id BIGINT,
    novel_name VARCHAR(100),
    basic_info JSON COMMENT '基础信息',
    core_traits TEXT COMMENT '3-5个核心性格特质',
    speaking_style TEXT COMMENT '说话风格描述',
    forbidden_behaviors TEXT COMMENT '绝不做的事',
    key_relationships JSON COMMENT '关键关系',
    representative_examples JSON COMMENT '代表性样本ID',
    build_version VARCHAR(20) DEFAULT 'v1.0.0',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    INDEX idx_character (character_id)
) COMMENT '角色画像摘要';

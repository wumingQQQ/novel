package com.wuming.novel.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum JobStage {
    PENDING(0, "队列中"),
    CHAPTER_SPLIT(1, "章节切分"),
    CHAPTER_ANALYSIS(2, "章节分析"),
    PASSAGE_BUILD(3, "Passage构建"),
    ROLE_EXAMPLE_BUILD(4, "角色样本构建"),
    REACTION_RULE_BUILD(5, "反应规则构建"),
    ROLE_PROFILE_BUILD(6, "角色画像构建"),
    COMPLETE(100, "完成");

    @EnumValue
    private final int code;

    @JsonValue
    private final String desc;

    JobStage(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

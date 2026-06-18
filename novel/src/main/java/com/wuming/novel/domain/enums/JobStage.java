package com.wuming.novel.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum JobStage {
    PENDING(0, "队列中"),
    CHAPTER_SPLIT(1, "章节切分"),
    LAYER_SPLIT(2, "剧情分层"),
    SCENE_SPLIT(3, "场景切分"),
    POOL_CLASSIFY(4, "场景分池"),
    EVIDENCE_EXTRACT(5, "证据提取"),
    PROFILE_AGGREGATION(6, "聚合画像"),
    COMPLETE(7, "完成");



    @EnumValue
    private final int code;

    @JsonValue
    private final String desc;

    JobStage(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

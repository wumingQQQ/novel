package com.wuming.novel.domain.enums;

/** 小说公共预处理的阶段顺序。 */
public enum NovelPreprocessStage {
    NONE(0),
    CHAPTER_SPLIT(1),
    PASSAGE_BUILD(3);

    private final int code;

    NovelPreprocessStage(int code) {
        this.code = code;
    }

    /** 判断当前阶段是否已经包含目标阶段的公共产物。 */
    public boolean covers(NovelPreprocessStage target) {
        return code >= target.code;
    }
}

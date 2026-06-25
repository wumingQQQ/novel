package com.wuming.novel.domain.llmresponse;

import java.util.List;

public record SceneSplitResponseWrapper(
        List<String> anchors
) {
}

package com.wuming.novel.domain.llmresponse;

import java.util.List;

public record ScenePoolResponseWrapper(
        List<ScenePoolResponse> pools
) {
}

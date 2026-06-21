package com.wuming.novel.domain.llmresponse;

import java.util.List;

public record LayerSplitResponseWrapper(
        List<LayerSplitResponse> layers
) {
}

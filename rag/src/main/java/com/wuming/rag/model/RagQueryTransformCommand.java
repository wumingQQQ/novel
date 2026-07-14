package com.wuming.rag.model;

public record RagQueryTransformCommand(
        String query,
        boolean multiQuery
) {
}

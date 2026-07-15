package com.wuming.rag.service;

import com.wuming.rag.model.RagQueryTransformCommand;
import dev.langchain4j.rag.query.Query;

import java.util.List;

public class DefaultRagQueryTransformer implements RagQueryTransformer {

    @Override
    public List<Query> transform(RagQueryTransformCommand command) {
        if (command == null || command.query() == null || command.query().isBlank()) {
            throw new IllegalArgumentException("RAG查询改写query不能为空");
        }
        return List.of(Query.from(command.query().trim()));
    }
}

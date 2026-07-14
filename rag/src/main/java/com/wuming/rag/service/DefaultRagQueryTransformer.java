package com.wuming.rag.service;

import com.wuming.rag.model.RagQueryTransformCommand;
import dev.langchain4j.rag.query.Query;

import java.util.List;

public class DefaultRagQueryTransformer implements RagQueryTransformer {

    @Override
    public List<Query> transform(RagQueryTransformCommand command) {
        return List.of(Query.from(command.query()));
    }
}

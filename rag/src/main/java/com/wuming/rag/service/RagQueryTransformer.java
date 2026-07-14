package com.wuming.rag.service;

import com.wuming.rag.model.RagQueryTransformCommand;
import dev.langchain4j.rag.query.Query;

import java.util.List;

public interface RagQueryTransformer {

    List<Query> transform(RagQueryTransformCommand command);
}

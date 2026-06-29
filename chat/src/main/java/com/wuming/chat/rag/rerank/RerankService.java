package com.wuming.chat.rag.rerank;

import java.util.List;

public interface RerankService {
    List<RerankedDocument> rerank(String query, List<RerankDocument> documents);
}

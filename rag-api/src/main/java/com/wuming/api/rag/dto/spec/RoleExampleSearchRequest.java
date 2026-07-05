package com.wuming.api.rag.dto.spec;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class RoleExampleSearchRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String indexName;
    private Long characterId;
    private String query;
    private Integer topK;       // 向量召回候选数量
    private boolean rerank = true;  // 是否重排序
    private Integer topN;       // 最终返回数量
}

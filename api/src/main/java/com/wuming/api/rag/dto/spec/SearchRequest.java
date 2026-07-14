package com.wuming.api.rag.dto.spec;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class SearchRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String indexName;
    private String query;       // 主查询文本，用于单路召回和重排序
    private List<String> queries;   // 多路召回查询文本，非空时优先用于向量召回
    private List<SearchFilter> filters;  // 元数据过滤条件，由业务模块构造，RAG模块转换执行
    private Integer topK;       // 向量召回候选数量
    private boolean rerank = true;  // 是否重排序
    private Integer topN;       // 最终返回数量
}

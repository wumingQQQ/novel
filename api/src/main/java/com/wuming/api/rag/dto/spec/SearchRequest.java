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
    private String query;       // 主查询文本，RAG模块会根据multiQuery决定是否扩展为多路查询
    private boolean multiQuery; // 是否由RAG模块基于query生成多路召回查询
    private List<SearchFilter> filters;  // 元数据过滤条件，由业务模块构造，RAG模块转换执行
    private Integer topK;       // 向量召回候选数量
    private Integer topN;       // 最终返回数量
}

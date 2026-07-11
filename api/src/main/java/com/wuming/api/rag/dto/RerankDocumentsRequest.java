package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 对调用方提供的有限文档集合执行重排序的请求。
 */
@Data
public class RerankDocumentsRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 用于衡量文档相关性的查询文本。 */
    private String query;

    /** 待重排序文档，服务不会从向量库额外召回。 */
    private List<RagDocument> documents;

    /** 最终保留数量；为空时返回全部排序结果。 */
    private Integer topN;
}

package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量删除文档的请求dto
 */
@Data
public class DeleteDocumentRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String indexName;
    private List<String> documentIds;
}

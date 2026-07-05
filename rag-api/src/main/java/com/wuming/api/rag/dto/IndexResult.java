package com.wuming.api.rag.dto;

import com.wuming.api.rag.enums.IndexStatus;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class IndexResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String code;
    private String message;
    private String indexName;
    private IndexStatus status;

    public static IndexResult success(String indexName, IndexStatus status) {
        IndexResult result = new IndexResult();
        result.setSuccess(true);
        result.setCode("OK");
        result.setIndexName(indexName);
        result.setStatus(status);
        return result;
    }

    public static IndexResult failure(String indexName, String code, String message) {
        IndexResult result = new IndexResult();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        result.setIndexName(indexName);
        result.setStatus(IndexStatus.FAILED);
        return result;
    }
}

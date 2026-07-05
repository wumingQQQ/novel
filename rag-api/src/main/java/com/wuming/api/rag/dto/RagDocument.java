package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 将要被向量化的文档
 */
@Data
public class RagDocument implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String documentId;
    private String content;
    private Map<String, Object> metadata;
}

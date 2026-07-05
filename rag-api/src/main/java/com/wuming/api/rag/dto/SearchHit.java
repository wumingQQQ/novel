package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * rag命中响应
 */
@Data
public class SearchHit implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String documentId;
    private String text;
    private double score;
    private Map<String, Object> metadata = new LinkedHashMap<>();
}

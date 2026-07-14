package com.wuming.api.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * rag命中响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHit implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String documentId;
    private String content;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private double score;
}

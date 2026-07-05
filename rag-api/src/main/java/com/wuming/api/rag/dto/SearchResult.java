package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class SearchResult implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String code;
    private String message;
    private List<RagHitDto> hits = new ArrayList<>();

    public static SearchResult success(List<RagHitDto> hits) {
        SearchResult result = new SearchResult();
        result.setSuccess(true);
        result.setCode("OK");
        result.setHits(hits == null ? List.of() : hits);
        return result;
    }

    public static SearchResult failure(String code, String message) {
        SearchResult result = new SearchResult();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}

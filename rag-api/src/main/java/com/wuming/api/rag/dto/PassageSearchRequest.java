package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class PassageSearchRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long novelId;
    private String query;
    private Integer topK;
}

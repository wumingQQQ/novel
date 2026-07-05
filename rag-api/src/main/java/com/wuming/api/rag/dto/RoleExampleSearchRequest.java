package com.wuming.api.rag.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class RoleExampleSearchRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long characterId;
    private String query;
    private Integer topK;
}

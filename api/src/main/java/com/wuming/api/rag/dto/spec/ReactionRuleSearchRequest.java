package com.wuming.api.rag.dto.spec;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class ReactionRuleSearchRequest extends SearchRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long characterId;
}

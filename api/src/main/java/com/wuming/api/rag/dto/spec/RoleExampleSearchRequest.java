package com.wuming.api.rag.dto.spec;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class RoleExampleSearchRequest extends SearchRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long characterId;

    /**
     * 评测运行时需要在向量召回前排除的原作 Passage。
     */
    private Long excludedPassageId;
}

package com.wuming.api.rag.dto.spec;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 查询novel passage
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class PassageSearchRequest extends SearchRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long novelId;
}

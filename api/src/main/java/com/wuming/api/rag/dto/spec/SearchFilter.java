package com.wuming.api.rag.dto.spec;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * RAG通用元数据过滤条件。
 *
 * <p>该DTO只表达业务过滤意图，不暴露底层向量库或LangChain4j实现类型。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchFilter implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String field;
    private Operator operator;
    private Object value;
    private List<Object> values;

    public static SearchFilter eq(String field, Object value) {
        return new SearchFilter(field, Operator.EQ, value, null);
    }

    public static SearchFilter ne(String field, Object value) {
        return new SearchFilter(field, Operator.NE, value, null);
    }

    public static SearchFilter in(String field, Collection<?> values) {
        return new SearchFilter(field, Operator.IN, null, copyValues(values));
    }

    public static SearchFilter notIn(String field, Collection<?> values) {
        return new SearchFilter(field, Operator.NOT_IN, null, copyValues(values));
    }

    public static SearchFilter containsString(String field, String value) {
        return new SearchFilter(field, Operator.CONTAINS_STRING, value, null);
    }

    public static SearchFilter tagContains(String field, Object value) {
        return new SearchFilter(field, Operator.TAG_CONTAINS, value, null);
    }

    private static List<Object> copyValues(Collection<?> values) {
        return values == null ? null : values.stream().map(value -> (Object) value).toList();
    }

    public enum Operator {
        EQ,
        NE,
        IN,
        NOT_IN,
        CONTAINS_STRING,
        TAG_CONTAINS
    }
}

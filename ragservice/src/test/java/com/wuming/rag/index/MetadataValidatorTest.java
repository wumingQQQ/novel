package com.wuming.rag.index;

import com.wuming.api.rag.dto.RagDocumentDto;
import com.wuming.api.rag.enums.MetadataFieldType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetadataValidatorTest {

    private final MetadataValidator validator = new MetadataValidator();

    @Test
    void shouldAcceptMetadataDeclaredByIndexDefinition() {
        RagIndexDefinition definition = definition(
                Map.of(
                        "characterId", MetadataFieldType.NUMERIC,
                        "sampleType", MetadataFieldType.TAG
                )
        );
        RagDocumentDto document = document(
                "role_example:1",
                "原作样本文本",
                Map.of(
                        "characterId", 1001L,
                        "sampleType", "INTERACTION"
                )
        );

        assertThatCode(() -> validator.validateDocument(definition, document))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMetadataNotDeclaredByIndexDefinition() {
        RagIndexDefinition definition = definition(Map.of("characterId", MetadataFieldType.NUMERIC));
        RagDocumentDto document = document(
                "role_example:1",
                "原作样本文本",
                Map.of(
                        "characterId", 1001L,
                        "unknownField", "unexpected"
                )
        );

        assertThatThrownBy(() -> validator.validateDocument(definition, document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metadata字段未在索引中声明: unknownField");
    }

    @Test
    void shouldRejectBlankDocumentText() {
        RagIndexDefinition definition = definition(Map.of("characterId", MetadataFieldType.NUMERIC));
        RagDocumentDto document = document(
                "role_example:1",
                " ",
                Map.of("characterId", 1001L)
        );

        assertThatThrownBy(() -> validator.validateDocument(definition, document))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("document.text不能为空");
    }

    @Test
    void shouldRejectBlankIndexName() {
        RagIndexDefinition definition = new RagIndexDefinition();
        definition.setIndexName("");
        definition.setKeyPrefix("rag:test:");
        definition.setVectorDimension(1024);

        assertThatThrownBy(() -> validator.validateDefinition(definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("indexName不能为空");
    }

    private RagIndexDefinition definition(Map<String, MetadataFieldType> fields) {
        RagIndexDefinition definition = new RagIndexDefinition();
        definition.setIndexName("idx:rag:test");
        definition.setKeyPrefix("rag:test:");
        definition.setVectorDimension(1024);
        definition.setMetadataFields(fields);
        return definition;
    }

    private RagDocumentDto document(String documentId, String text, Map<String, Object> metadata) {
        RagDocumentDto document = new RagDocumentDto();
        document.setDocumentId(documentId);
        document.setText(text);
        document.setMetadata(metadata);
        return document;
    }
}

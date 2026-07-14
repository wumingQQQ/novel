package com.wuming.novel.passage.split;

import com.wuming.novel.config.PassageSplitProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 按字符长度重叠切分Passage。
 *
 * <p>具体切分委托给LangChain4j recursive splitter，start/end记录切片在原章节正文中的字符位置。</p>
 */
@Component
@RequiredArgsConstructor
public class CharacterOverlapPassageSplitStrategy implements PassageSplitStrategy {
    private final PassageSplitProperties properties;

    @Override
    public PassageSplitStrategyType type() {
        return PassageSplitStrategyType.CHARACTER_OVERLAP;
    }

    @Override
    public List<PassageSlice> split(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String normalizedContent = normalizeLineEndings(content);

        PassageSplitProperties.CharacterWindow characterConfig = properties.getCharacter();
        int maxChars = characterConfig.getMaxChars();
        int overlapChars = characterConfig.getOverlapChars();
        validate(maxChars, overlapChars);

        DocumentSplitter splitter = DocumentSplitters.recursive(maxChars, overlapChars);
        List<TextSegment> segments = splitter.split(Document.from(normalizedContent));
        return toSlices(normalizedContent, segments);
    }

    /**
     * 将LangChain4j切分结果转换为业务Passage，并补齐原文字符位置。
     */
    private List<PassageSlice> toSlices(String source, List<TextSegment> segments) {
        List<PassageSlice> slices = new ArrayList<>(segments.size());
        int previousStart = 0;
        for (TextSegment segment : segments) {
            String text = segment.text();
            if (text == null || text.isBlank()) {
                continue;
            }
            int startIndex = locateSegment(source, text, previousStart);
            int endIndex = startIndex + text.length();
            // 字符位置采用1-based inclusive，便于人工核对原文位置。
            slices.add(new PassageSlice(slices.size() + 1, startIndex + 1, endIndex, text));
            previousStart = startIndex;
        }
        return slices;
    }

    /**
     * recursive splitter会产生重叠片段，所以定位时从上一个片段起点继续查找，而不是从上一个片段终点查找。
     */
    private int locateSegment(String source, String text, int searchFrom) {
        int startIndex = source.indexOf(text, searchFrom);
        if (startIndex >= 0) {
            return startIndex;
        }
        startIndex = source.indexOf(text);
        if (startIndex >= 0) {
            return startIndex;
        }
        throw new IllegalStateException("LangChain4j切分结果无法定位到原文字符位置");
    }

    /**
     * 统一换行符，避免不同操作系统文本换行影响recursive切分结果回定位。
     */
    private String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    /**
     * 提前校验配置，避免重叠长度不合理导致切分无法推进。
     */
    private void validate(int maxChars, int overlapChars) {
        if (maxChars <= 0) {
            throw new IllegalStateException("novel.passage.character.max-chars 必须大于0");
        }
        if (overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalStateException("novel.passage.character.overlap-chars 必须大于等于0且小于max-chars");
        }
    }
}

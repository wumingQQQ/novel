package com.wuming.novel.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.NovelPassage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
class NovelPassageSplitter {
    private static final int WINDOW_SIZE = 15;
    private static final int OVERLAP_SIZE = 3;
    private static final String ANALYSIS_DONE = "DONE";
    private static final String VECTOR_PENDING = "PENDING";

    private final ObjectMapper objectMapper;

    NovelPassageSplitter() {
        this(new ObjectMapper());
    }

    NovelPassageSplitter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<NovelPassage> split(Chapter chapter, int startSequence) {
        List<String> paragraphs = paragraphs(chapter.getContent());
        if (paragraphs.isEmpty()) {
            return List.of();
        }
        List<Range> ranges = ANALYSIS_DONE.equals(chapter.getAnalysisStatus())
                ? sceneRanges(chapter, paragraphs.size())
                : slidingWindowRanges(paragraphs.size());
        List<NovelPassage> passages = new ArrayList<>(ranges.size());
        for (int i = 0; i < ranges.size(); i++) {
            Range range = ranges.get(i);
            NovelPassage passage = new NovelPassage();
            passage.setNovelId(chapter.getNovelId());
            passage.setChapterId(chapter.getId());
            passage.setContent(String.join("\n", paragraphs.subList(range.start() - 1, range.end())));
            passage.setSequence(startSequence + i);
            passage.setChapterSequence(i + 1);
            passage.setWordCount(passage.getContent().length());
            passage.setStartParagraph(range.start());
            passage.setEndParagraph(range.end());
            passage.setVectorStatus(VECTOR_PENDING);
            passages.add(passage);
        }
        return passages;
    }

    private List<String> paragraphs(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> paragraphs = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String paragraph = line.trim();
            if (!paragraph.isEmpty()) {
                paragraphs.add(paragraph);
            }
        }
        return paragraphs;
    }

    private List<Range> sceneRanges(Chapter chapter, int paragraphCount) {
        List<Integer> boundaries = sceneBoundaries(chapter);
        if (boundaries.isEmpty()) {
            return List.of(new Range(1, paragraphCount));
        }
        List<Integer> starts = new ArrayList<>();
        starts.add(1);
        for (Integer boundary : boundaries) {
            if (boundary != null && boundary > 1 && boundary <= paragraphCount) {
                starts.add(boundary);
            }
        }
        return toRanges(starts, paragraphCount);
    }

    private List<Integer> sceneBoundaries(Chapter chapter) {
        String rawBoundaries = chapter.getSceneBoundaries();
        if (rawBoundaries == null || rawBoundaries.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawBoundaries, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("章节场景边界解析失败，chapterId: {}, sceneBoundaries: {}",
                    chapter.getId(), rawBoundaries, e);
            return List.of();
        }
    }

    private List<Range> slidingWindowRanges(int paragraphCount) {
        List<Range> ranges = new ArrayList<>();
        int step = WINDOW_SIZE - OVERLAP_SIZE;
        for (int start = 1; start <= paragraphCount; start += step) {
            int end = Math.min(start + WINDOW_SIZE - 1, paragraphCount);
            ranges.add(new Range(start, end));
            if (end == paragraphCount) {
                break;
            }
        }
        return ranges;
    }

    private List<Range> toRanges(List<Integer> starts, int paragraphCount) {
        List<Integer> sortedStarts = starts.stream()
                .distinct()
                .sorted()
                .toList();
        List<Range> ranges = new ArrayList<>();
        for (int i = 0; i < sortedStarts.size(); i++) {
            int start = sortedStarts.get(i);
            int end = i + 1 < sortedStarts.size()
                    ? sortedStarts.get(i + 1) - 1
                    : paragraphCount;
            if (start <= end) {
                ranges.add(new Range(start, end));
            }
        }
        return ranges;
    }

    private record Range(int start, int end) {
    }
}

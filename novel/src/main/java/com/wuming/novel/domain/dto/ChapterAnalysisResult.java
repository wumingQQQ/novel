package com.wuming.novel.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 章节分析结果。
 */
@Data
public class ChapterAnalysisResult {
    private String summary;
    private List<String> mainCharacters = new ArrayList<>();
    private List<Integer> sceneBoundaries = new ArrayList<>();
}

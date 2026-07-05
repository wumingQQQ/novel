package com.wuming.novel.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM Passage 出场人物识别结果。
 */
@Data
public class PassageCharacterResult {
    private List<String> characters = new ArrayList<>();
}

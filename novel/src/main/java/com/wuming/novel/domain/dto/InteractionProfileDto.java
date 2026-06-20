package com.wuming.novel.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InteractionProfileDto {
    private String protagonistName;
    private String tone;
    private List<String> keyEvents = new ArrayList<>();
    private List<String> conversationSamples = new ArrayList<>();
}

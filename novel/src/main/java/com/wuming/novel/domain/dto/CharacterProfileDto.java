package com.wuming.novel.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CharacterProfileDto {
    private BasicSettingDto basicSetting = new BasicSettingDto();
    private List<String> coreTraits = new ArrayList<>();
    private String valueSystem;
    private String behaviorPatterns;
    private String emotionalPatterns;
    private String relationshipAttitude;
    private String weaknesses;
    private SpeechStyleDto speechStyle = new SpeechStyleDto();

    @Data
    public static class BasicSettingDto {
        private String characterName;
        private int age;
        private String identity;
        private String presume;
    }

    @Data
    public static class SpeechStyleDto {
        private String tone;
        private String wordsHabit;
        private List<String> representativeLines = new ArrayList<>();
    }
}

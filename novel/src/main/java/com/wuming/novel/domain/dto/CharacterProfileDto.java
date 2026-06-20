package com.wuming.novel.domain.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CharacterProfileDto {
    private BasicSettingDto basicSetting = new BasicSettingDto();
    private String personality;
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

package com.wuming.novel.domain.dto;

import com.wuming.novel.domain.entity.CharacterProfile;
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

    public static CharacterProfileDto fromEntity(CharacterProfile entity) {
        CharacterProfileDto dto = new CharacterProfileDto();
        if (entity == null) {
            return dto;
        }

        CharacterProfile.BasicSetting basicSetting = entity.getBasicSetting();
        if (basicSetting != null) {
            BasicSettingDto basicSettingDto = new BasicSettingDto();
            basicSettingDto.setCharacterName(basicSetting.getCharacterName());
            basicSettingDto.setAge(basicSetting.getAge());
            basicSettingDto.setIdentity(basicSetting.getIdentity());
            basicSettingDto.setPresume(basicSetting.getPresume());
            dto.setBasicSetting(basicSettingDto);
        }
        dto.setCoreTraits(copyList(entity.getCoreTraits()));
        dto.setValueSystem(entity.getValueSystem());
        dto.setBehaviorPatterns(entity.getBehaviorPatterns());
        dto.setEmotionalPatterns(entity.getEmotionalPatterns());
        dto.setRelationshipAttitude(entity.getRelationshipAttitude());
        dto.setWeaknesses(entity.getWeaknesses());

        CharacterProfile.SpeechStyle speechStyle = entity.getSpeechStyle();
        if (speechStyle != null) {
            SpeechStyleDto speechStyleDto = new SpeechStyleDto();
            speechStyleDto.setTone(speechStyle.getTone());
            speechStyleDto.setWordsHabit(speechStyle.getWordsHabit());
            speechStyleDto.setRepresentativeLines(copyList(speechStyle.getRepresentativeLines()));
            dto.setSpeechStyle(speechStyleDto);
        }
        return dto;
    }

    public CharacterProfile toEntity() {
        CharacterProfile entity = new CharacterProfile();
        CharacterProfile.BasicSetting basicSetting = new CharacterProfile.BasicSetting();
        if (getBasicSetting() != null) {
            basicSetting.setCharacterName(getBasicSetting().getCharacterName());
            basicSetting.setAge(getBasicSetting().getAge());
            basicSetting.setIdentity(getBasicSetting().getIdentity());
            basicSetting.setPresume(getBasicSetting().getPresume());
        }
        entity.setBasicSetting(basicSetting);
        entity.setCoreTraits(copyList(getCoreTraits()));
        entity.setValueSystem(getValueSystem());
        entity.setBehaviorPatterns(getBehaviorPatterns());
        entity.setEmotionalPatterns(getEmotionalPatterns());
        entity.setRelationshipAttitude(getRelationshipAttitude());
        entity.setWeaknesses(getWeaknesses());

        SpeechStyleDto dto = getSpeechStyle();
        CharacterProfile.SpeechStyle speechStyle = new CharacterProfile.SpeechStyle();
        if (dto != null) {
            speechStyle.setTone(dto.getTone());
            speechStyle.setWordsHabit(dto.getWordsHabit());
            speechStyle.setRepresentativeLines(copyList(dto.getRepresentativeLines()));
        }
        entity.setSpeechStyle(speechStyle);
        return entity;
    }

    private static List<String> copyList(List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

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

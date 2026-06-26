package com.wuming.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@TableName(value = "character_profiles", autoResultMap = true)
public class CharacterProfile {
    @TableId
    private Long id;
    private Long jobId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private BasicSetting basicSetting = new BasicSetting();
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> coreTraits = new ArrayList<>();
    private String valueSystem;
    private String behaviorPatterns;
    private String emotionalPatterns;
    private String relationshipAttitude;
    private String weaknesses;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private SpeechStyle speechStyle = new SpeechStyle();

    @Data
    public static class BasicSetting {
        private String characterName;
        private int age;
        private String identity;
        private String presume;
    }

    @Data
    public static class SpeechStyle {
        private String tone;
        private String wordsHabit;
        private List<String> representativeLines = new ArrayList<>();
    }
}

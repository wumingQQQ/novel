package com.wuming.api.profile.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class CharacterProfileDto implements Serializable {
    private BasicSettingDto basicSetting;
    private List<String> coreTraits;
    private String valueSystem;
    private String behaviorPatterns;
    private String emotionalPatterns;
    private String relationshipAttitude;
    private String weaknesses;
    private SpeechStyleDto speechStyle;

    @Serial
    private static final long serialVersionUID = 1L;
}

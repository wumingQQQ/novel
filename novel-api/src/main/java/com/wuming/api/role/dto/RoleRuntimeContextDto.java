package com.wuming.api.role.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色聊天运行时上下文。
 */
@Data
public class RoleRuntimeContextDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long novelId;
    private String novelName;
    private Long characterId;
    private String characterName;
    private String buildStatus;
    private BasicInfo basicInfo = new BasicInfo();
    private String coreTraits;
    private SpeakingStyle speakingStyle = new SpeakingStyle();
    private String forbiddenBehaviors;

    /**
     * 角色基础信息。
     */
    @Data
    public static class BasicInfo implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String age;
        private String gender;
        private String occupation;
        private String appearance;
    }

    /**
     * 角色说话风格。
     */
    @Data
    public static class SpeakingStyle implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String signature;
        private List<String> distinctivePatterns = new ArrayList<>();
        private List<String> avoidPatterns = new ArrayList<>();
    }
}

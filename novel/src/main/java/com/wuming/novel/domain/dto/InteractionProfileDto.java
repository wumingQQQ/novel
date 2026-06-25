package com.wuming.novel.domain.dto;

import com.wuming.novel.domain.entity.InteractionProfile;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InteractionProfileDto {
    private String protagonistName;
    private String tone;
    private List<String> keyEvents = new ArrayList<>();
    private List<String> conversationSamples = new ArrayList<>();

    public static InteractionProfileDto fromEntity(InteractionProfile entity) {
        InteractionProfileDto dto = new InteractionProfileDto();
        if (entity == null) {
            return dto;
        }

        dto.setProtagonistName(entity.getProtagonistName());
        dto.setTone(entity.getTone());
        dto.setKeyEvents(copyList(entity.getKeyEvents()));
        dto.setConversationSamples(copyList(entity.getConversationSamples()));
        return dto;
    }

    public InteractionProfile toEntity() {
        InteractionProfile entity = new InteractionProfile();
        entity.setProtagonistName(getProtagonistName());
        entity.setTone(getTone());
        entity.setKeyEvents(copyList(getKeyEvents()));
        entity.setConversationSamples(copyList(getConversationSamples()));
        return entity;
    }

    private static List<String> copyList(List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }
}

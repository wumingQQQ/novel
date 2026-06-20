package com.wuming.novel.domain.dto;

import lombok.Data;

@Data
public class FullPortraitDto {
    private CharacterProfileDto characterProfile = new CharacterProfileDto();
    private InteractionProfileDto interactionProfile = new InteractionProfileDto();
}

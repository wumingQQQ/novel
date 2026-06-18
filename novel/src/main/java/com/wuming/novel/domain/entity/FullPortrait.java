package com.wuming.novel.domain.entity;

import lombok.Data;

@Data
public class FullPortrait {
    CharacterProfile characterProfile = new CharacterProfile();
    InteractionProfile interactionProfile = new InteractionProfile();
}

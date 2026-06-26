package com.wuming.chat.domain.model;

import com.wuming.chat.domain.entity.CharacterProfile;
import com.wuming.chat.domain.entity.InteractionProfile;
import com.wuming.chat.domain.entity.Job;

public record RoleProfileContext(
        Job job,
        CharacterProfile characterProfile,
        InteractionProfile interactionProfile
) {
}

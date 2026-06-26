package com.wuming.chat.domain.model;

import com.wuming.chat.domain.entity.CharacterProfile;
import com.wuming.chat.domain.entity.InteractionProfile;
import com.wuming.chat.domain.entity.Job;
import com.wuming.chat.domain.entity.Novel;

public record RoleProfileContext(
        Job job,
        Novel novel,
        CharacterProfile characterProfile,
        InteractionProfile interactionProfile
) {
}

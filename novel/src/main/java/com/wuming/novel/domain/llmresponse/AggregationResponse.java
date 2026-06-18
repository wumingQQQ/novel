package com.wuming.novel.domain.llmresponse;

import com.wuming.novel.domain.entity.CharacterProfile;
import com.wuming.novel.domain.entity.InteractionProfile;

public record AggregationResponse(
        CharacterProfile characterProfile,
        InteractionProfile interactionProfile
) {
}

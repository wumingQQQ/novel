package com.wuming.novel.domain.llmresponse;

import com.wuming.novel.domain.dto.CharacterProfileDto;
import com.wuming.novel.domain.dto.InteractionProfileDto;

public record AggregationResponse(
        CharacterProfileDto characterProfile,
        InteractionProfileDto interactionProfile
) {
}

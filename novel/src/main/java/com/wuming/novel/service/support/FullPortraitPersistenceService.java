package com.wuming.novel.service.support;

import com.wuming.novel.domain.dto.CharacterProfileDto;
import com.wuming.novel.domain.dto.FullPortraitDto;
import com.wuming.novel.domain.dto.InteractionProfileDto;
import com.wuming.novel.domain.entity.CharacterProfile;
import com.wuming.novel.domain.entity.InteractionProfile;
import com.wuming.novel.service.ICharacterProfileService;
import com.wuming.novel.service.IInteractionProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FullPortraitPersistenceService {
    private final ICharacterProfileService characterProfileService;
    private final IInteractionProfileService interactionProfileService;

    public FullPortraitDto getByJobId(Long jobId) {
        CharacterProfile characterProfile = characterProfileService.lambdaQuery()
                .eq(CharacterProfile::getJobId, jobId)
                .one();
        InteractionProfile interactionProfile = interactionProfileService.lambdaQuery()
                .eq(InteractionProfile::getJobId, jobId)
                .one();
        if (characterProfile == null || interactionProfile == null) {
            return null;
        }

        FullPortraitDto fullPortrait = new FullPortraitDto();
        fullPortrait.setCharacterProfile(CharacterProfileDto.fromEntity(characterProfile));
        fullPortrait.setInteractionProfile(InteractionProfileDto.fromEntity(interactionProfile));
        return fullPortrait;
    }

    @Transactional
    public void replace(Long jobId, FullPortraitDto fullPortrait) {
        characterProfileService.lambdaUpdate()
                .eq(CharacterProfile::getJobId, jobId)
                .remove();
        interactionProfileService.lambdaUpdate()
                .eq(InteractionProfile::getJobId, jobId)
                .remove();

        CharacterProfile characterProfile = fullPortrait.getCharacterProfile().toEntity();
        characterProfile.setJobId(jobId);
        characterProfileService.save(characterProfile);

        InteractionProfile interactionProfile = fullPortrait.getInteractionProfile().toEntity();
        interactionProfile.setJobId(jobId);
        interactionProfile.setCharacterId(characterProfile.getId());
        interactionProfileService.save(interactionProfile);
    }
}

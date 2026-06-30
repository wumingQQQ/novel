package com.wuming.novel.integration.rpc.profile;

import com.wuming.api.profile.dto.*;
import com.wuming.novel.domain.dto.FullPortraitDto;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Novel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * 负责将模块内部entity/dto转为api包中的dto
 */
@Component
public class RoleContextAssembler {
    public RoleContextDto toRoleContextDto(
            Job job, Novel novel, FullPortraitDto fullPortrait
    ) {
        RoleContextDto dto = new RoleContextDto();
        dto.setJobId(job.getId());
        dto.setNovelId(novel.getId());
        dto.setNovelName(novel.getName());
        dto.setProtagonistName(job.getProtagonistName());
        dto.setCharacterProfile(toCharacterProfileDto(fullPortrait.getCharacterProfile()));
        dto.setInteractionProfile(toInteractionProfile(fullPortrait.getInteractionProfile()));

        return dto;
    }

    /**
     * 将本模块内的CharacterProfileDto拷贝到api模块中的对应类
     */
    private CharacterProfileDto toCharacterProfileDto(
            com.wuming.novel.domain.dto.CharacterProfileDto source
    ) {
        CharacterProfileDto target = new CharacterProfileDto();

        if(source == null){
            return target;
        }

        target.setBasicSetting(toBasicSetting(source.getBasicSetting()));
        target.setCoreTraits(copyList(source.getCoreTraits()));
        target.setValueSystem(source.getValueSystem());
        target.setBehaviorPatterns(source.getBehaviorPatterns());
        target.setEmotionalPatterns(source.getEmotionalPatterns());
        target.setRelationshipAttitude(source.getRelationshipAttitude());
        target.setWeaknesses(source.getWeaknesses());
        target.setSpeechStyle(toSpeechStyle(source.getSpeechStyle()));
        return target;
    }

    private BasicSettingDto toBasicSetting(
            com.wuming.novel.domain.dto.CharacterProfileDto.BasicSettingDto source
    ) {
        BasicSettingDto target = new BasicSettingDto();
        if (source == null) {
            return target;
        }

        target.setCharacterName(source.getCharacterName());
        target.setAge(source.getAge());
        target.setIdentity(source.getIdentity());
        target.setPresume(source.getPresume());
        return target;
    }

    private SpeechStyleDto toSpeechStyle(
            com.wuming.novel.domain.dto.CharacterProfileDto.SpeechStyleDto source
    ) {
        SpeechStyleDto target = new SpeechStyleDto();
        if (source == null) {
            return target;
        }

        target.setTone(source.getTone());
        target.setWordsHabit(source.getWordsHabit());
        target.setRepresentativeLines(copyList(source.getRepresentativeLines()));
        return target;
    }

    /**
     * 将本模块内的InteractionProfileDto拷贝到api模块中的对应类
     */
    private InteractionProfileDto toInteractionProfile(
            com.wuming.novel.domain.dto.InteractionProfileDto source
    ) {
        InteractionProfileDto target = new InteractionProfileDto();
        if (source == null) {
            return target;
        }

        target.setTone(source.getTone());
        target.setKeyEvents(copyList(source.getKeyEvents()));
        target.setConversationSamples(copyList(source.getConversationSamples()));
        return target;
    }

    private ArrayList<String> copyList(java.util.List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }
}

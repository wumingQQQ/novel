package com.wuming.novel.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.domain.dto.CharacterProfileDto;
import com.wuming.novel.domain.dto.FullPortraitDto;
import com.wuming.novel.domain.dto.InteractionProfileDto;
import com.wuming.novel.domain.entity.CharacterProfile;
import com.wuming.novel.domain.entity.Evidence;
import com.wuming.novel.domain.entity.InteractionProfile;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Layer;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.domain.llmresponse.AggregationResponse;
import com.wuming.novel.exception.LLMResponseEmptyException;
import com.wuming.novel.service.ICharacterProfileService;
import com.wuming.novel.service.IEvidenceService;
import com.wuming.novel.service.IInteractionProfileService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ILayerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AggregationService {

    private final IEvidenceService evidenceService;
    private final ILayerService layerService;
    private final IJobService jobService;
    private final ICharacterProfileService characterProfileService;
    private final IInteractionProfileService interactionProfileService;
    private final PromptConfig promptConfig;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    @Lazy
    @Autowired
    private AggregationService self;


    public AggregationService(IEvidenceService evidenceService, ILayerService layerService, IJobService jobService, ICharacterProfileService characterProfileService, IInteractionProfileService interactionProfileService, PromptConfig promptConfig, ChatModel chatModel, ObjectMapper objectMapper) {
        this.evidenceService = evidenceService;
        this.layerService = layerService;
        this.jobService = jobService;
        this.characterProfileService = characterProfileService;
        this.interactionProfileService = interactionProfileService;
        this.promptConfig = promptConfig;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
    }


    public boolean aggregation(Long jobId) {
        Job job = jobService.getById(jobId);
        if(job.getStage().getCode() >= JobStage.PROFILE_AGGREGATION.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.PROFILE_AGGREGATION);
            return true;
        }

        Long novelId = job.getNovelId();
        String protagonistName = job.getProtagonistName();
        String targetName = job.getTargetName();

        try {
            // 清除旧画像
            deleteExistingPortrait(jobId);

            List<Layer> layers = layerService.lambdaQuery().eq(Layer::getNovelId, novelId).orderByAsc(Layer::getLayerIndex).list();
            FullPortraitDto fullPortrait = new FullPortraitDto();
            fullPortrait.getCharacterProfile().getBasicSetting().setCharacterName(targetName);
            fullPortrait.getInteractionProfile().setProtagonistName(protagonistName);
            for(Layer layer : layers) {
                for(PoolType poolType : PoolType.values()) {
                    List<Evidence> evidences = evidenceService.lambdaQuery()
                            .eq(Evidence::getNovelId, novelId)
                            .eq(Evidence::getLayerId, layer.getId())
                            .eq(Evidence::getPoolType, poolType)
                            .eq(Evidence::getJobId, job.getId())
                            .list();

                    if(evidences.isEmpty()) {
                        continue;
                    }

                    log.debug("job: {} layer: {} pool: {} 聚合证据数: {}", jobId, layer.getId(), poolType, evidences.size());
                    List<List<Evidence>> partition = Lists.partition(evidences, 20);
                    for(List<Evidence> evidenceList : partition) {
                        AggregationResponse response = aggregationPool(evidenceList, layer, fullPortrait);
                        if(response == null){
                            throw new LLMResponseEmptyException("聚合服务时llm返回空");
                        }

                        copyAggregationResponse(fullPortrait, response);
                        log.debug("job: {} layer: {} pool: {} 画像聚合批次完成，批次证据数: {}", jobId, layer.getId(), poolType, evidenceList.size());
                    }
                }
            }
            self.saveProfile(fullPortrait, jobId);
            return true;
        } catch (Exception e) {
            log.error("job:{}：画像聚合失败",  jobId, e);
            return false;
        }
    }

    private void deleteExistingPortrait(Long jobId) {
        characterProfileService.lambdaUpdate().
                eq(CharacterProfile::getJobId, jobId)
                .remove();
        interactionProfileService.lambdaUpdate()
                .eq(InteractionProfile::getJobId, jobId)
                .remove();
    }

    @Transactional
    public void saveProfile(FullPortraitDto fullPortrait, Long jobId) {
        CharacterProfile characterProfile = toCharacterProfileEntity(fullPortrait.getCharacterProfile());
        characterProfile.setJobId(jobId);
        InteractionProfile interactionProfile = toInteractionProfileEntity(fullPortrait.getInteractionProfile());
        interactionProfile.setJobId(jobId);
        characterProfileService.save(characterProfile);
        interactionProfile.setCharacterId(characterProfile.getId());
        interactionProfileService.save(interactionProfile);
        log.debug("job: {} 画像保存完成，characterProfileId: {}, interactionProfileId: {}", jobId, characterProfile.getId(), interactionProfile.getId());
    }


    private AggregationResponse aggregationPool(List<Evidence> evidences, Layer layer, FullPortraitDto fullPortrait) {

        return chatClient.prompt()
                .user(u -> {
                            try {
                                u.text(promptConfig.getAggregationPrompt())
                                        .param("layerName", layer.getLayerName())
                                        .param("poolType", evidences.get(0).getPoolType().name())
                                        .param("evidences", formatEvidences(evidences))
                                        // 将之前的画像填入
                                        .param("currentProfile", objectMapper.writeValueAsString(fullPortrait));
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        }
                )
                .options(OpenAiChatOptions.builder()
                        .responseFormat(ResponseFormat.builder()
                                .type(ResponseFormat.Type.JSON_OBJECT)
                                .build())
                        .build()
                )
                .call()
                .entity(AggregationResponse.class);
    }

    private void copyAggregationResponse(FullPortraitDto fullPortrait, AggregationResponse aggregationResponse) {
        fullPortrait.setCharacterProfile(aggregationResponse.characterProfile());
        fullPortrait.setInteractionProfile(aggregationResponse.interactionProfile());
    }

    private String formatEvidences(List<Evidence> evidences) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < evidences.size(); i++) {
            Evidence e = evidences.get(i);
            sb.append("【证据").append(i + 1).append("】\n");
            sb.append("结论：").append(e.getConclusion()).append("\n");
            sb.append("置信度：").append(String.format("%.2f", e.getConfidence())).append("\n");
            sb.append("原文支撑：\n");
            for (String quote : e.getSupportingQuotes()) {
                sb.append("  > \"").append(quote).append("\"\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private CharacterProfile toCharacterProfileEntity(CharacterProfileDto dto) {
        CharacterProfile entity = new CharacterProfile();
        if (dto == null) {
            return entity;
        }

        CharacterProfile.BasicSetting basicSetting = new CharacterProfile.BasicSetting();
        if (dto.getBasicSetting() != null) {
            basicSetting.setCharacterName(dto.getBasicSetting().getCharacterName());
            basicSetting.setAge(dto.getBasicSetting().getAge());
            basicSetting.setIdentity(dto.getBasicSetting().getIdentity());
            basicSetting.setPresume(dto.getBasicSetting().getPresume());
        }
        entity.setBasicSetting(basicSetting);
        entity.setPersonality(dto.getPersonality());

        CharacterProfile.SpeechStyle speechStyle = new CharacterProfile.SpeechStyle();
        if (dto.getSpeechStyle() != null) {
            speechStyle.setTone(dto.getSpeechStyle().getTone());
            speechStyle.setWordsHabit(dto.getSpeechStyle().getWordsHabit());
            speechStyle.setRepresentativeLines(dto.getSpeechStyle().getRepresentativeLines());
        }
        entity.setSpeechStyle(speechStyle);

        return entity;
    }

    private InteractionProfile toInteractionProfileEntity(InteractionProfileDto dto) {
        InteractionProfile entity = new InteractionProfile();
        if (dto == null) {
            return entity;
        }

        entity.setProtagonistName(dto.getProtagonistName());
        entity.setTone(dto.getTone());
        entity.setKeyEvents(dto.getKeyEvents());
        entity.setConversationSamples(dto.getConversationSamples());
        return entity;
    }

}

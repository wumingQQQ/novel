package com.wuming.novel.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.domain.entity.*;
import com.wuming.novel.domain.enums.PoolType;
import com.wuming.novel.domain.llmresponse.AggregationResponse;
import com.wuming.novel.service.ICharacterProfileService;
import com.wuming.novel.service.IEvidenceService;
import com.wuming.novel.service.IInteractionProfileService;
import com.wuming.novel.service.ILayerService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class AggregationService {

    private final IEvidenceService evidenceService;
    private final ILayerService layerService;
    private final ICharacterProfileService characterProfileService;
    private final IInteractionProfileService interactionProfileService;
    private final PromptConfig promptConfig;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;


    public AggregationService(IEvidenceService evidenceService, ILayerService layerService, ICharacterProfileService characterProfileService, IInteractionProfileService interactionProfileService, PromptConfig promptConfig, ChatModel chatModel, ObjectMapper objectMapper) {
        this.evidenceService = evidenceService;
        this.layerService = layerService;
        this.characterProfileService = characterProfileService;
        this.interactionProfileService = interactionProfileService;
        this.promptConfig = promptConfig;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aggregation(int novelId) {
        List<Layer> layers = layerService.lambdaQuery().eq(Layer::getNovelId, novelId).orderByAsc(Layer::getLayerIndex).list();
        FullPortrait fullPortrait = new FullPortrait();
        for(Layer layer : layers) {
            for(PoolType poolType : PoolType.values()) {
                // TODO 后续考虑使用jobId对证据再进行过滤
                List<Evidence> evidences = evidenceService.lambdaQuery()
                        .eq(Evidence::getNovelId, novelId)
                        .eq(Evidence::getLayerId, layer.getId())
                        .eq(Evidence::getPoolType, poolType)
                        .list();

                if(evidences.isEmpty()) {
                    continue;
                }

                AggregationResponse response = aggregationPool(evidences, layer, fullPortrait);
                copyAggregationResponse(fullPortrait, response);
            }
        }
        saveProfile(fullPortrait);
    }

    @Transactional
    public void saveProfile(FullPortrait fullPortrait) {
        CharacterProfile characterProfile = fullPortrait.getCharacterProfile();
        InteractionProfile interactionProfile = fullPortrait.getInteractionProfile();
        characterProfileService.save(characterProfile);
        interactionProfile.setCharacterId(characterProfile.getId());
        interactionProfileService.save(interactionProfile);
    }

    private AggregationResponse aggregationPool(List<Evidence> evidences, Layer layer, FullPortrait  fullPortrait) {

        return chatClient.prompt()
                .user(u -> {
                            try {
                                u.text(promptConfig.getAggregationPrompt())
                                        .param("layerName", layer.getLayerName())
                                        .param("evidences", evidences)
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

    private void copyAggregationResponse(FullPortrait fullPortrait, AggregationResponse aggregationResponse) {
        fullPortrait.setCharacterProfile(aggregationResponse.characterProfile());
        fullPortrait.setInteractionProfile(aggregationResponse.interactionProfile());
    }

}

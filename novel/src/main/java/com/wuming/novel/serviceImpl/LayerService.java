package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Layer;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.domain.llmresponse.LayerSplitResponse;
import com.wuming.novel.mapper.LayerMapper;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ILayerService;
import com.wuming.novel.service.INovelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class LayerService extends ServiceImpl<LayerMapper, Layer> implements ILayerService {

    private final LayerMapper layerMapper;
    private final INovelService novelService;
    private final IChapterService chapterService;
    private final PromptConfig promptConfig;
    private final ChatClient chatClient;
    private final IJobService jobService;

    public LayerService(LayerMapper layerMapper, INovelService novelService, IChapterService chapterService, PromptConfig promptConfig, ChatModel chatModel, IJobService jobService) {
        this.layerMapper = layerMapper;
        this.novelService = novelService;
        this.chapterService = chapterService;
        this.promptConfig = promptConfig;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.jobService = jobService;
    }

    @Value("${novel.layer.min-chapter-per-layer}")
    private int minChapterSize;
    @Value("${novel.layer.max-chapter-per-layer}")
    private int maxChapterSize;
    @Value("${novel.layer.min-layer-size}")
    private int minLayerSize;
    @Value("${novel.layer.max-layer-size}")
    private int maxLayerSize;

    @Override
    @Transactional
    public void splitLayer(int jobId) {
        int novelId = jobService.getById(jobId).getNovelId();
        // 幂等设计
        cleanOldLayer(novelId);


        List<String> chapterTitles = chapterService.lambdaQuery()
                .eq(Chapter::getNovelId, novelId)
                .select(Chapter::getTitle)
                .orderByAsc(Chapter::getSequence)
                .list()
                .stream()
                .map(Chapter::getTitle)
                .toList();
        Novel novel = novelService.getById(novelId);
        if(novel == null) {
            throw new IllegalArgumentException("小说" + novelId + "不存在，请检查是否已经删除");
        }
        String novelName = novel.getName();
        int chapterCount = chapterTitles.size();
        if(chapterCount == 0){
            System.out.println("小说" + novelId + "章节数据不存在，请检查后重试");
            return;
        }

        LayerSplitResponse[] responses = chatClient.prompt()
                .user(u -> u.text(promptConfig.getLayerSplitPrompt())
                        .param("novelName", novelName)
                        .param("totalChapters", chapterCount)
                        .param("minChaptersPerLayer", minChapterSize)
                        .param("maxChaptersPerLayer", maxChapterSize)
                        .param("minLayers", minLayerSize)
                        .param("maxLayers", maxLayerSize)
                        .param("chapterList", String.join("\n", chapterTitles))
                )
                .options(OpenAiChatOptions.builder()
                        .responseFormat(ResponseFormat.builder()
                                .type(ResponseFormat.Type.JSON_OBJECT)
                                .build())
                        .build()
                )
                .call()
                .entity(LayerSplitResponse[].class);

        if(responses == null || responses.length == 0) {
            System.out.println("llm对layer split响应为空");
            // TODO 考虑增加降级逻辑，固定章节数分层
            return;
        }

        List<Layer> layers = new ArrayList<>();
        for (LayerSplitResponse layerResponse : responses) {
            Layer layer = new Layer();
            layer.setLayerIndex(layerResponse.layerIndex());
            layer.setLayerName(layerResponse.layerName());
            layer.setNovelId(novelId);
            layer.setStartChapterSequence(layerResponse.startChapter());
            layer.setEndChapterSequence(layerResponse.endChapter());

            layers.add(layer);
        }
        saveBatch(layers);
    }

    private void cleanOldLayer(int novelId) {
        QueryWrapper<Layer> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("novel_id", novelId);
        int delete = layerMapper.delete(queryWrapper);
        if(delete > 0) {
            log.info("清理小说{}的旧分层，数量为{}", novelId, delete);
        }

    }
}

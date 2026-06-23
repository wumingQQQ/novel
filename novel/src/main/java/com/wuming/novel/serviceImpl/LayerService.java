package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.config.llm.LlmClientFactory;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Layer;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.llmresponse.LayerSplitResponse;
import com.wuming.novel.domain.llmresponse.LayerSplitResponseWrapper;
import com.wuming.novel.exception.LLMResponseEmptyException;
import com.wuming.novel.mapper.LayerMapper;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ILayerService;
import com.wuming.novel.service.INovelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LayerService extends ServiceImpl<LayerMapper, Layer> implements ILayerService {

    private final LayerMapper layerMapper;
    private final INovelService novelService;
    private final IChapterService chapterService;
    private final PromptConfig promptConfig;
    private final ChatClient chatClient;
    private final IJobService jobService;
    @Lazy
    @Autowired
    private LayerService self;

    public LayerService(LayerMapper layerMapper, INovelService novelService, IChapterService chapterService, PromptConfig promptConfig, LlmClientFactory clientFactory, IJobService jobService) {
        this.layerMapper = layerMapper;
        this.novelService = novelService;
        this.chapterService = chapterService;
        this.promptConfig = promptConfig;
        this.chatClient = clientFactory.defaultClient();
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
    public void splitLayer(Long jobId) {
        Job job = jobService.getById(jobId);
        if(job.getStage().getCode() >= JobStage.LAYER_SPLIT.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.LAYER_SPLIT);
            return;
        }

        Long novelId = job.getNovelId();
        try {
            List<Chapter> chapters = chapterService.lambdaQuery()
                    .eq(Chapter::getNovelId, novelId)
                    .select(Chapter::getSequence, Chapter::getTitle)
                    .orderByAsc(Chapter::getSequence)
                    .list();
            Novel novel = novelService.getById(novelId);
            String novelName = novel.getName();

            int chapterCount = chapters.size();
            if(chapterCount == 0){
                throw new IllegalStateException("小说" + novelId + "的章节数据为空，请检查后重试");
            }

            String chapterList = chapters.stream()
                    .map(chapter -> chapter.getSequence() + ". " + chapter.getTitle())
                    .collect(Collectors.joining("\n"));

            log.debug("job: {} 小说{}开始剧情分层，章节数: {}", jobId, novelId, chapterCount);
            LayerSplitResponseWrapper responseWrapper = chatClient.prompt()
                    .user(u -> u.text(promptConfig.getLayerSplitPrompt())
                            .param("novelName", novelName)
                            .param("totalChapters", chapterCount)
                            .param("minChaptersPerLayer", minChapterSize)
                            .param("maxChaptersPerLayer", maxChapterSize)
                            .param("minLayers", minLayerSize)
                            .param("maxLayers", maxLayerSize)
                            .param("chapterList", chapterList)
                    )
                    .call()
                    .entity(LayerSplitResponseWrapper.class);

            if(responseWrapper == null || responseWrapper.layers() == null || responseWrapper.layers().isEmpty()) {
                throw new LLMResponseEmptyException("小说" + novelId + "分层时llm响应为空，请稍后重试");
                // TODO 考虑增加降级逻辑，固定章节数分层
            }

            // 从llm响应中解析layer
            List<LayerSplitResponse> responses = responseWrapper.layers();
            List<Layer> layers = extractLayers(responses, novelId);

            self.saveLayers(novelId, layers);
            log.debug("job: {} 小说{}剧情分层完成，层数: {}", jobId, novelId, layers.size());
        } catch (Exception e) {
            log.error("job: {}剧情分层失败", jobId, e);
            throw new RuntimeException("剧情分层失败", e);
        }
    }

    private static List<Layer> extractLayers(List<LayerSplitResponse> responses, Long novelId) {
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
        return layers;
    }

    @Transactional
    public void saveLayers(Long novelId, List<Layer> layers) {
        cleanOldLayer(novelId);
        saveBatch(layers);
    }

    private void cleanOldLayer(Long novelId) {
        QueryWrapper<Layer> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("novel_id", novelId);
        int delete = layerMapper.delete(queryWrapper);
        if(delete > 0) {
            log.info("清理小说{}的旧分层，数量为{}", novelId, delete);
        }

    }
}

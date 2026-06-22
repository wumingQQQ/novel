package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.config.PromptConfig;
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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
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
    public boolean splitLayer(Long jobId) {
        Job job = jobService.getById(jobId);
        if(job.getStage().getCode() >= JobStage.LAYER_SPLIT.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.LAYER_SPLIT);
            return true;
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
                log.warn("小说{}的章节数据为空，请检查后重试", novelId);
                return false;
            }

            String chapterList = chapters.stream()
                    .map(chapter -> chapter.getSequence() + ". " + chapter.getTitle())
                    .collect(Collectors.joining("\n"));
            LayerConstraints constraints = buildLayerConstraints(chapterCount);

            log.debug("job: {} 小说{}开始剧情分层，章节数: {}", jobId, novelId, chapterCount);
            LayerSplitResponseWrapper responseWrapper = chatClient.prompt()
                    .user(u -> u.text(promptConfig.getLayerSplitPrompt())
                            .param("novelName", novelName)
                            .param("totalChapters", chapterCount)
                            .param("minChaptersPerLayer", constraints.minChapterSize())
                            .param("maxChaptersPerLayer", constraints.maxChapterSize())
                            .param("minLayers", constraints.minLayerSize())
                            .param("maxLayers", constraints.maxLayerSize())
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
            validateLayers(responses, constraints, chapterCount);
            List<Layer> layers = extractLayers(responses, novelId);

            self.saveLayers(novelId, layers);
            log.debug("job: {} 小说{}剧情分层完成，层数: {}", jobId, novelId, layers.size());
            return true;
        } catch (Exception e) {
            log.error("job: {}剧情分层失败", jobId, e);
            return false;
        }
    }

    private void validateLayers(List<LayerSplitResponse> responses, LayerConstraints constraints, int chapterCount) {
        int layerCount = responses.size();
        if (layerCount < constraints.minLayerSize() || layerCount > constraints.maxLayerSize()) {
            throw new IllegalArgumentException("分层数量不符合约束: actual=" + layerCount
                    + ", expected=" + constraints.minLayerSize() + "-" + constraints.maxLayerSize());
        }

        int expectedStart = 1;
        for (LayerSplitResponse response : responses) {
            if (response.startChapter() != expectedStart) {
                throw new IllegalArgumentException("分层章节不连续: layerIndex=" + response.layerIndex()
                        + ", expectedStart=" + expectedStart + ", actualStart=" + response.startChapter());
            }
            if (response.endChapter() < response.startChapter()) {
                throw new IllegalArgumentException("分层结束章节小于起始章节: layerIndex=" + response.layerIndex());
            }

            int layerChapterCount = response.endChapter() - response.startChapter() + 1;
            if (layerChapterCount < constraints.minChapterSize() || layerChapterCount > constraints.maxChapterSize()) {
                throw new IllegalArgumentException("分层章节数不符合约束: layerIndex=" + response.layerIndex()
                        + ", actual=" + layerChapterCount
                        + ", expected=" + constraints.minChapterSize() + "-" + constraints.maxChapterSize());
            }
            expectedStart = response.endChapter() + 1;
        }

        if (expectedStart != chapterCount + 1) {
            throw new IllegalArgumentException("分层未覆盖全部章节: expectedEnd=" + chapterCount
                    + ", actualEnd=" + (expectedStart - 1));
        }
    }

    private LayerConstraints buildLayerConstraints(int chapterCount) {
        int effectiveMinChapterSize = Math.min(minChapterSize, chapterCount);
        int effectiveMaxChapterSize = Math.min(maxChapterSize, chapterCount);
        if (effectiveMaxChapterSize < effectiveMinChapterSize) {
            effectiveMaxChapterSize = effectiveMinChapterSize;
        }

        int minFeasibleLayers = ceilDiv(chapterCount, effectiveMaxChapterSize);
        int maxFeasibleLayers = Math.max(1, chapterCount / effectiveMinChapterSize);
        int effectiveMinLayerSize = Math.max(minFeasibleLayers, Math.min(minLayerSize, maxFeasibleLayers));
        int effectiveMaxLayerSize = Math.min(maxLayerSize, maxFeasibleLayers);
        if (effectiveMaxLayerSize < effectiveMinLayerSize) {
            effectiveMaxLayerSize = effectiveMinLayerSize;
        }

        if (effectiveMinChapterSize != minChapterSize
                || effectiveMaxChapterSize != maxChapterSize
                || effectiveMinLayerSize != minLayerSize
                || effectiveMaxLayerSize != maxLayerSize) {
            log.debug("章节数{}触发分层约束调整: 每层章节 {}-{} -> {}-{}, 层数 {}-{} -> {}-{}",
                    chapterCount,
                    minChapterSize,
                    maxChapterSize,
                    effectiveMinChapterSize,
                    effectiveMaxChapterSize,
                    minLayerSize,
                    maxLayerSize,
                    effectiveMinLayerSize,
                    effectiveMaxLayerSize);
        }

        return new LayerConstraints(
                effectiveMinChapterSize,
                effectiveMaxChapterSize,
                effectiveMinLayerSize,
                effectiveMaxLayerSize
        );
    }

    private int ceilDiv(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }

    private record LayerConstraints(
            int minChapterSize,
            int maxChapterSize,
            int minLayerSize,
            int maxLayerSize
    ) {
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

package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Sets;
import com.wuming.novel.config.PromptConfig;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.llmresponse.SceneSplitResponse;
import com.wuming.novel.mapper.SceneMapper;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ISceneService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class SceneService extends ServiceImpl<SceneMapper, Scene> implements ISceneService {
    private final SceneMapper sceneMapper;
    private final IChapterService chapterService;
    private final PromptConfig promptConfig;
    private final IJobService jobService;
    private final ChatClient chatClient;

    public SceneService(SceneMapper sceneMapper, IChapterService chapterService, PromptConfig promptConfig, IJobService jobService, ChatModel chatModel) {
        this.sceneMapper = sceneMapper;
        this.chapterService = chapterService;
        this.promptConfig = promptConfig;
        this.jobService = jobService;
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public void splitScene(int jobId) {
        Job job = jobService.getById(jobId);
        if(job.getStage().getCode() >= JobStage.SCENE_SPLIT.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.SCENE_SPLIT);
            return;
        }
        int novelId = jobService.getById(jobId).getNovelId();
        List<Integer> finishedChapterIds = queryFinishedChapter(novelId);

        Set<Integer> unfinishedChapterIds = computeUnfinishedChapterIds(finishedChapterIds, novelId);

        List<Chapter> chapters = chapterService.listByIds(unfinishedChapterIds);
        List<CompletableFuture<List<Scene>>> futures = chapters.stream()
                .map(this::splitOneChapter)
                .toList();

        // TODO 可以考虑使用thenAccept实现进度显示
        // 为测试考虑，暂时先join全部任务完成
        futures.forEach(CompletableFuture::join);
    }

    // 查询已经处理完成的章节id
    private List<Integer> queryFinishedChapter(int novelId){
        QueryWrapper<Scene> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("chapter_id")
                .eq("novel_id", novelId)
                .groupBy("chapter_id");
        return sceneMapper.selectList(queryWrapper).stream().map(Scene::getChapterId).toList();
    }

    private Set<Integer> computeUnfinishedChapterIds(List<Integer> finishedChapterIds, int novelId){
        List<Integer> allChapterIds = chapterService.lambdaQuery()
                .eq(Chapter::getNovelId, novelId)
                .select(Chapter::getId)
                .list()
                .stream()
                .map(Chapter::getId)
                .toList();

        return Sets.difference(
                new HashSet<>(allChapterIds),
                new HashSet<>(finishedChapterIds)
        );
    }


    @Async("sceneSplitExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected CompletableFuture<List<Scene>> splitOneChapter(Chapter chapter) {

        try {
            // TODO 使用提示词模版解析时可能与中文字符串符号发生冲突
            String promptText = promptConfig.getSceneSplitPrompt()
                    .replace("{chapterTitle}", chapter.getTitle())
                    .replace("{chapterContent}", chapter.getContent());
            BeanOutputConverter<SceneSplitResponse[]> converter = new BeanOutputConverter<>(SceneSplitResponse[].class);
            SceneSplitResponse[] splitResponses = chatClient.prompt()
                    .user(promptText)
                    .options(OpenAiChatOptions.builder()
                            .responseFormat(ResponseFormat.builder()
                                    .type(ResponseFormat.Type.JSON_OBJECT)
                                    .build())
                            .build())
                    .call()
                    .entity(SceneSplitResponse[].class);

            List<Scene> scenes = extractSceneFromChapter(chapter, splitResponses);
            saveBatch(scenes);
            System.out.printf("小说%d的章节%d处理成功\n", chapter.getNovelId(), chapter.getSequence());
            return CompletableFuture.completedFuture(scenes);
        } catch (Exception e) {
            // TODO 后面考虑增加记录重试逻辑
            System.out.printf("小说%d的章节%d处理失败\n", chapter.getNovelId(), chapter.getSequence());
            System.out.printf("异常信息：%s\n", e.getMessage());
            return CompletableFuture.failedFuture(e);
        }


    }

    private List<Scene> extractSceneFromChapter(Chapter chapter, SceneSplitResponse[] response) {
        List<Scene> scenes= new ArrayList<>();
        // TODO 如果llm返回结果为空，则按块切分
        if(response == null || response.length == 0){
            System.out.printf("小说%d的章节%d处理失败, llm返回结果为空\n", chapter.getNovelId(), chapter.getSequence());
            Scene scene = new Scene();
            scene.setNovelId(chapter.getNovelId());
            scene.setChapterId(chapter.getId());
            scene.setSequence(1);
            scene.setContent(chapter.getContent());
            return Collections.singletonList(scene);
        }

        String content = chapter.getContent();
        System.out.println("llm返回结果");
        for(SceneSplitResponse sceneSplitResponse : response){
            System.out.println(sceneSplitResponse);
        }

        for(int i = 0; i < response.length; i++){
            SceneSplitResponse current = response[i];

            Scene scene = new Scene();
            scene.setNovelId(chapter.getNovelId());
            scene.setChapterId(chapter.getId());
            scene.setSequence(i + 1);

            // 定位原文位置
            int startIndex = content.indexOf(current.anchor());
            int endIndex;
            if(i < response.length -1){
                SceneSplitResponse next = response[i + 1];
                endIndex = content.indexOf(next.anchor());
            }
            else{
                // 如果是最后一个场景，则结束位置为章节末尾
                endIndex = content.length();
            }

            // 边界保护
            if(startIndex != -1 && endIndex != -1 && startIndex < endIndex){
                String sceneContent = content.substring(startIndex, endIndex);
                scene.setContent(sceneContent);
                scenes.add(scene);
            }
            else{
                // 可能发生幻觉，原文位置找不到，或者顺序错乱
                System.out.printf("小说%d的章节%d匹配失败，无法切分\n", chapter.getNovelId(), chapter.getSequence());
                scene.setSequence(1);
                scene.setContent(chapter.getContent());
                return Collections.singletonList(scene);
            }
        }
        return scenes;
    }
}

package com.wuming.novel.service.support;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.dto.ChapterAnalysisResult;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterAnalysisService {
    private static final int CONTENT_LIMIT = 12000;
    private static final String SYSTEM_TEMPLATE_PATH = "prompts/system/chapter-analysis.st";
    private static final String USER_TEMPLATE_PATH = "prompts/user/chapter-analysis.st";

    private final IJobService jobService;
    private final IChapterService chapterService;
    private final ChatClient chatClient;
    private final PromptTemplateRenderer renderer;  // 负责加载Prompt并将参数进行渲染

    /**
     * 分析整本小说
     */
    public void analyze(Long jobId){
        Job job = jobService.getById(jobId);
        if(job == null){
            throw new BusinessException(ErrorCode.JOB_NOT_FOUND);
        }
        Long novelId = job.getNovelId();
        List<Chapter> chapters = chapterService.lambdaQuery().eq(Chapter::getNovelId, novelId).list();
        chapters.forEach(this::analyzeChapter);
    }

    /**
     * 分析单章并回写章节分析结果。
     *
     * <p>分析失败时会先将章节标记为FAILED，再继续抛出异常交给Pipeline记录失败项。</p>
     *
     * @param chapter 章节实体
     */
    public void analyzeChapter(Chapter chapter){
        try{
            PromptTemplateRenderer.DualPrompt dualPrompt = renderer.renderDual(
                    SYSTEM_TEMPLATE_PATH,
                    USER_TEMPLATE_PATH,
                    Map.of(
                            "chapterTitle", chapter.getTitle(),
                            "chapterContent", abbreviate(chapter.getContent())
                    )
            );
            ChapterAnalysisResult result = chatClient.prompt()
                    .system(dualPrompt.systemPrompt())
                    .user(dualPrompt.userPrompt())
                    .call()
                    .entity(ChapterAnalysisResult.class);
            if(result == null){
                throw new BusinessException(ErrorCode.LLM_EMPTY_RESPONSE);
            }

            // 更新章节未完成部分
            chapter.setSummary(result.summary());
            chapter.setSceneBoundaries(result.sceneBoundaries());
            chapter.setAnalysisStatus("DONE");
            chapter.setAnalysisError(null);
            chapter.setAnalyzedTime(LocalDateTime.now());
            chapterService.updateById(chapter);
        }
        catch (Exception e){
            // 记录章节分析失败信息
            chapter.setAnalysisStatus("FAILED");
            chapter.setAnalysisError(e.getMessage());
            chapterService.updateById(chapter);
            log.error("小说{}, 章节{}分析失败", chapter.getNovelId(), chapter.getId(), e);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("章节分析失败，chapterId: " + chapter.getId(), e);
        }
    }

    /**
     * 截断过长章节
     */
    private String abbreviate(String content) {
        if (content == null || content.length() <= CONTENT_LIMIT) {
            return content == null ? "" : content;
        }
        return content.substring(0, CONTENT_LIMIT);
    }

}

package com.wuming.novel.rpc.scene;

import com.wuming.api.scene.SceneFacade;
import com.wuming.api.scene.dto.SceneDto;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ISceneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

@Slf4j
@DubboService
@RequiredArgsConstructor
public class SceneFacadeImpl implements SceneFacade {
    private final SceneDtoAssembler assembler;
    private final IJobService jobService;
    private final ISceneService sceneService;
    private final IChapterService chapterService;

    /**
     * 查询指定任务下某章节的完整场景列表，供chat模块索引RAG向量使用。
     *
     * @param jobId 任务id
     * @param chapterId 章节id
     * @return 按场景序号升序排列的场景DTO列表
     */
    @Override
    public List<SceneDto> listScenesByChapter(Long jobId, Long chapterId) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId);
             TraceContext.MdcScope ignoredChapter = TraceContext.putChapterId(chapterId)) {
            long start = System.currentTimeMillis();
            Job job = requireJob(jobId);
            Chapter chapter = requireChapter(chapterId, job);

            List<Scene> scenes = sceneService.lambdaQuery()
                    .eq(Scene::getChapterId, chapterId)
                    .orderByAsc(Scene::getSequence)
                    .list();

            log.info("章节场景远程查询完成，sceneCount: {}, costMs: {}",
                    scenes.size(), System.currentTimeMillis() - start);
            return scenes.stream()
                    .map(scene -> assembler.toSceneDto(scene, chapter))
                    .toList();
        }
    }

    /**
     * 校验任务id并返回任务实体。
     */
    private Job requireJob(Long jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId不能为空");
        }
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        return job;
    }

    /**
     * 校验章节存在且属于任务关联小说。
     */
    private Chapter requireChapter(Long chapterId, Job job) {
        if (chapterId == null) {
            throw new IllegalArgumentException("chapterId不能为空");
        }
        Chapter chapter = chapterService.getById(chapterId);
        if (chapter == null) {
            throw new IllegalArgumentException("章节不存在");
        }
        if (!chapter.getNovelId().equals(job.getNovelId())) {
            throw new IllegalArgumentException("章节不属于该任务关联小说");
        }
        return chapter;
    }
}

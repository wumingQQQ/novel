package com.wuming.novel.rpc.scene;

import com.wuming.api.scene.dto.SceneDto;
import com.wuming.api.scene.SceneFacade;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Scene;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.ISceneService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

@DubboService
@RequiredArgsConstructor
public class SceneFacadeImpl implements SceneFacade {
    private final SceneDtoAssembler assembler;
    private final IJobService jobService;
    private final ISceneService sceneService;
    private final IChapterService chapterService;

    @Override
    public List<SceneDto> listScenesByChapter(Long jobId, Long chapterId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId不能为空");
        }
        if (chapterId == null) {
            throw new IllegalArgumentException("chapterId不能为空");
        }
        Job job = jobService.getById(jobId);
        if(job == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        Chapter chapter = chapterService.getById(chapterId);
        if (chapter == null) {
            throw new IllegalArgumentException("章节不存在");
        }
        if (!chapter.getNovelId().equals(job.getNovelId())) {
            throw new IllegalArgumentException("章节不属于该任务关联小说");
        }

        List<Scene> scenes = sceneService.lambdaQuery()
                .eq(Scene::getChapterId, chapterId)
                .orderByAsc(Scene::getSequence)
                .list();

        return scenes.stream()
                .map(scene -> assembler.toSceneDto(scene, chapter))
                .toList();
    }
}

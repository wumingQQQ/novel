package com.wuming.novel.pipeline.step;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.NovelPreprocessStage;
import com.wuming.novel.pipeline.support.AsyncStageItemRunner;
import com.wuming.novel.pipeline.support.PipelineJobSupport;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.INovelPassageService;
import com.wuming.novel.service.IPassageCharacterService;
import com.wuming.novel.service.support.NovelPreprocessCheckpointStore;
import com.wuming.novel.service.support.NovelPreprocessCoordinator;
import com.wuming.novel.service.support.NovelPreprocessProgress;
import com.wuming.novel.sse.JobProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PassageBuildStepTest {
    private static final long JOB_ID = 11L;
    private static final long NOVEL_ID = 42L;

    @Mock
    private PipelineJobSupport pipelineJobSupport;
    @Mock
    private AsyncStageItemRunner asyncStageItemRunner;
    @Mock
    private IChapterService chapterService;
    @Mock
    private INovelPassageService novelPassageService;
    @Mock
    private IPassageCharacterService passageCharacterService;
    @Mock
    private NovelPreprocessCoordinator preprocessCoordinator;
    @Mock
    private NovelPreprocessCheckpointStore checkpointStore;
    @Mock
    private JobProgressService jobProgressService;
    @Mock
    private Executor llmExecutor;

    private PassageBuildStep step;

    @BeforeEach
    void setUp() {
        step = new PassageBuildStep(
                pipelineJobSupport,
                asyncStageItemRunner,
                chapterService,
                novelPassageService,
                passageCharacterService,
                preprocessCoordinator,
                checkpointStore,
                jobProgressService,
                llmExecutor
        );
    }

    @Test
    void retriesOnlyFailedChapterAndNeverSplitsSuccessfulChapter() {
        Job job = job();
        Chapter successful = chapter(1L, 1);
        Chapter failed = chapter(2L, 2);
        when(pipelineJobSupport.requireJob(JOB_ID)).thenReturn(job);
        when(chapterService.list(org.mockito.ArgumentMatchers.<Wrapper<Chapter>>any()))
                .thenReturn(List.of(successful, failed));
        when(checkpointStore.selectItems(NOVEL_ID, NovelPreprocessStage.PASSAGE_BUILD, List.of(1L, 2L)))
                .thenReturn(List.of(2L));
        when(novelPassageService.splitPassage(JOB_ID, 2L)).thenReturn(List.of(new NovelPassage()));
        when(checkpointStore.progress(NOVEL_ID, NovelPreprocessStage.PASSAGE_BUILD))
                .thenReturn(new NovelPreprocessProgress(2, 0, 1));
        runCoordinatorWork();

        step.execute(JOB_ID);

        verify(novelPassageService).splitPassage(JOB_ID, 2L);
        verify(novelPassageService, never()).splitPassage(JOB_ID, 1L);
        verify(checkpointStore).selectItems(NOVEL_ID, NovelPreprocessStage.PASSAGE_BUILD, List.of(1L, 2L));
        verify(checkpointStore).recordSuccess(NOVEL_ID, NovelPreprocessStage.PASSAGE_BUILD, 2L);
        InOrder order = inOrder(checkpointStore, jobProgressService);
        order.verify(checkpointStore).recordSuccess(NOVEL_ID, NovelPreprocessStage.PASSAGE_BUILD, 2L);
        order.verify(jobProgressService).setStageItemCounts(JOB_ID, JobStage.PASSAGE_BUILD, 2, 2, 0);
    }

    @Test
    void coordinatorSnapshotMapsToCurrentJobStageCounts() {
        Job job = job();
        when(pipelineJobSupport.requireJob(JOB_ID)).thenReturn(job);
        when(chapterService.list(org.mockito.ArgumentMatchers.<Wrapper<Chapter>>any()))
                .thenReturn(List.of(chapter(1L, 1), chapter(2L, 2), chapter(3L, 3)));
        NovelPreprocessProgress snapshot = new NovelPreprocessProgress(2, 1, 1);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<NovelPreprocessProgress> observer = invocation.getArgument(3);
            observer.accept(snapshot);
            return null;
        }).when(preprocessCoordinator).execute(
                eq(NOVEL_ID),
                eq(NovelPreprocessStage.PASSAGE_BUILD),
                any(NovelPreprocessCoordinator.StageWork.class),
                any()
        );

        step.execute(JOB_ID);

        verify(jobProgressService).setStageItemCounts(JOB_ID, JobStage.PASSAGE_BUILD, 3, 2, 1);
    }

    private void runCoordinatorWork() {
        doAnswer(invocation -> {
            NovelPreprocessCoordinator.StageWork work = invocation.getArgument(2);
            work.run();
            return null;
        }).when(preprocessCoordinator).execute(
                eq(NOVEL_ID),
                eq(NovelPreprocessStage.PASSAGE_BUILD),
                any(NovelPreprocessCoordinator.StageWork.class),
                any()
        );
    }

    private Job job() {
        Job job = new Job();
        job.setId(JOB_ID);
        job.setNovelId(NOVEL_ID);
        return job;
    }

    private Chapter chapter(long id, int sequence) {
        Chapter chapter = new Chapter();
        chapter.setId(id);
        chapter.setNovelId(NOVEL_ID);
        chapter.setSequence(sequence);
        return chapter;
    }
}

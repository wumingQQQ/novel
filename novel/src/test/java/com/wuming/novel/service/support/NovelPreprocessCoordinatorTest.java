package com.wuming.novel.service.support;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.wuming.novel.domain.entity.NovelPreprocess;
import com.wuming.novel.domain.enums.NovelPreprocessStage;
import com.wuming.novel.domain.enums.NovelPreprocessStatus;
import com.wuming.novel.infrastructure.mapper.NovelPreprocessMapper;
import com.wuming.novel.pipeline.lock.NovelPreprocessLock;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.INovelPassageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelPreprocessCoordinatorTest {
    private static final long NOVEL_ID = 42L;
    private static final NovelPreprocessStage STAGE = NovelPreprocessStage.PASSAGE_BUILD;

    @Mock
    private NovelPreprocessMapper preprocessMapper;

    @Mock
    private NovelPreprocessLock preprocessLock;

    @Mock
    private IChapterService chapterService;

    @Mock
    private INovelPassageService novelPassageService;

    @Mock
    private NovelPreprocessCheckpointStore checkpointStore;

    @Mock
    private NovelPreprocessCoordinator.StageWork work;

    private NovelPreprocessCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new NovelPreprocessCoordinator(
                preprocessMapper,
                preprocessLock,
                chapterService,
                novelPassageService,
                checkpointStore,
                3
        );
    }

    @Test
    void readyPreprocessReusesOutputWithoutRunningWork() {
        when(preprocessMapper.selectOne(any())).thenReturn(preprocess(1L, NovelPreprocessStage.PASSAGE_BUILD));

        coordinator.execute(NOVEL_ID, STAGE, work);

        verifyNoInteractions(work);
        verifyNoInteractions(checkpointStore);
    }

    @Test
    void exhaustedSharedAttemptsMarkFailedAndRejectWork() {
        NovelPreprocess preprocess = preprocess(1L, NovelPreprocessStage.CHAPTER_SPLIT);
        when(preprocessMapper.selectOne(any())).thenReturn(preprocess);
        when(preprocessLock.tryLock(NOVEL_ID)).thenReturn("lock-token");
        when(checkpointStore.tryStartAttempt(NOVEL_ID, STAGE, 3L)).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> coordinator.execute(NOVEL_ID, STAGE, work));

        assertEquals("公共预处理超过共享最大尝试次数", exception.getMessage());
        ArgumentCaptor<NovelPreprocess> update = ArgumentCaptor.forClass(NovelPreprocess.class);
        verify(preprocessMapper).updateById(update.capture());
        assertEquals(NovelPreprocessStatus.FAILED, update.getValue().getStatus());
        assertEquals("公共预处理超过共享最大尝试次数", update.getValue().getFailureReason());
        verify(preprocessLock).release(NOVEL_ID, "lock-token");
        verifyNoInteractions(work);
    }

    @Test
    void successfulWorkClearsSharedCheckpointAfterMarkingSucceeded() {
        NovelPreprocess preprocess = preprocess(1L, NovelPreprocessStage.CHAPTER_SPLIT);
        when(preprocessMapper.selectOne(any())).thenReturn(preprocess);
        when(preprocessLock.tryLock(NOVEL_ID)).thenReturn("lock-token");
        when(checkpointStore.tryStartAttempt(NOVEL_ID, STAGE, 3L)).thenReturn(true);
        when(chapterService.count(any())).thenReturn(4L);
        when(novelPassageService.count(any())).thenReturn(8L);

        coordinator.execute(NOVEL_ID, STAGE, work);

        org.mockito.InOrder order = inOrder(preprocessMapper, checkpointStore);
        order.verify(preprocessMapper, times(2))
                .update(any(NovelPreprocess.class), any(UpdateWrapper.class));
        order.verify(checkpointStore).clear(NOVEL_ID, STAGE);
        verify(preprocessLock).release(NOVEL_ID, "lock-token");
    }

    @Test
    void postLockCheckpointFailureReleasesLockToken() {
        NovelPreprocess preprocess = preprocess(1L, NovelPreprocessStage.CHAPTER_SPLIT);
        when(preprocessMapper.selectOne(any())).thenReturn(preprocess);
        when(preprocessLock.tryLock(NOVEL_ID)).thenReturn("lock-token");
        when(checkpointStore.tryStartAttempt(NOVEL_ID, STAGE, 3L))
                .thenThrow(new IllegalStateException("checkpoint unavailable"));

        assertThrows(IllegalStateException.class, () -> coordinator.execute(NOVEL_ID, STAGE, work));

        verify(preprocessLock).release(NOVEL_ID, "lock-token");
    }

    @Test
    void checkpointCleanupFailureLeavesSuccessfulPreprocessReady() {
        NovelPreprocess preprocess = preprocess(1L, NovelPreprocessStage.CHAPTER_SPLIT);
        when(preprocessMapper.selectOne(any())).thenReturn(preprocess);
        when(preprocessLock.tryLock(NOVEL_ID)).thenReturn("lock-token");
        when(checkpointStore.tryStartAttempt(NOVEL_ID, STAGE, 3L)).thenReturn(true);
        when(chapterService.count(any())).thenReturn(4L);
        when(novelPassageService.count(any())).thenReturn(8L);
        doThrow(new IllegalStateException("checkpoint unavailable"))
                .when(checkpointStore).clear(NOVEL_ID, STAGE);

        assertDoesNotThrow(() -> coordinator.execute(NOVEL_ID, STAGE, work));

        ArgumentCaptor<NovelPreprocess> update = ArgumentCaptor.forClass(NovelPreprocess.class);
        verify(preprocessMapper, times(2)).update(update.capture(), any(UpdateWrapper.class));
        assertEquals(NovelPreprocessStatus.READY, update.getAllValues().get(1).getStatus());
        verify(preprocessLock).release(NOVEL_ID, "lock-token");
    }

    @Test
    void successUpdateExplicitlyClearsCurrentStageAndFailureReason() {
        NovelPreprocess preprocess = preprocess(1L, NovelPreprocessStage.CHAPTER_SPLIT);
        when(preprocessMapper.selectOne(any())).thenReturn(preprocess);
        when(preprocessLock.tryLock(NOVEL_ID)).thenReturn("lock-token");
        when(checkpointStore.tryStartAttempt(NOVEL_ID, STAGE, 3L)).thenReturn(true);
        when(chapterService.count(any())).thenReturn(4L);
        when(novelPassageService.count(any())).thenReturn(8L);

        coordinator.execute(NOVEL_ID, STAGE, work);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<NovelPreprocess>> wrapper = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(preprocessMapper, times(2)).update(any(NovelPreprocess.class), wrapper.capture());
        assertTrue(wrapper.getAllValues().get(0).getSqlSet().contains("failure_reason"));
        assertTrue(wrapper.getAllValues().get(1).getSqlSet().contains("current_stage"));
        assertTrue(wrapper.getAllValues().get(1).getSqlSet().contains("failure_reason"));
    }

    @Test
    void waitingInvocationPublishesCheckpointSnapshotBeforeReusingOutput() {
        NovelPreprocess running = preprocess(1L, NovelPreprocessStage.CHAPTER_SPLIT);
        NovelPreprocess ready = preprocess(1L, NovelPreprocessStage.PASSAGE_BUILD);
        NovelPreprocessProgress progress = new NovelPreprocessProgress(7, 2, 1);
        @SuppressWarnings("unchecked")
        Consumer<NovelPreprocessProgress> observer = org.mockito.Mockito.mock(Consumer.class);
        when(preprocessMapper.selectOne(any())).thenReturn(running, ready);
        when(preprocessLock.tryLock(NOVEL_ID)).thenReturn(null);
        when(checkpointStore.progress(NOVEL_ID, STAGE)).thenReturn(progress);
        doNothing().when(observer).accept(progress);

        coordinator.execute(NOVEL_ID, STAGE, work, observer);

        verify(observer).accept(progress);
        verifyNoInteractions(work);
        verify(preprocessLock, never()).release(eq(NOVEL_ID), any());
    }

    @Test
    void observerFailureDoesNotAbortWaitingInvocation() {
        NovelPreprocess running = preprocess(1L, NovelPreprocessStage.CHAPTER_SPLIT);
        NovelPreprocess ready = preprocess(1L, NovelPreprocessStage.PASSAGE_BUILD);
        NovelPreprocessProgress progress = new NovelPreprocessProgress(7, 2, 1);
        @SuppressWarnings("unchecked")
        Consumer<NovelPreprocessProgress> observer = org.mockito.Mockito.mock(Consumer.class);
        when(preprocessMapper.selectOne(any())).thenReturn(running, ready);
        when(preprocessLock.tryLock(NOVEL_ID)).thenReturn(null);
        when(checkpointStore.progress(NOVEL_ID, STAGE)).thenReturn(progress);
        doThrow(new IllegalStateException("observer unavailable")).when(observer).accept(progress);

        assertDoesNotThrow(() -> coordinator.execute(NOVEL_ID, STAGE, work, observer));

        verify(observer).accept(progress);
        verifyNoInteractions(work);
    }

    private NovelPreprocess preprocess(long id, NovelPreprocessStage completedStage) {
        NovelPreprocess preprocess = new NovelPreprocess();
        preprocess.setId(id);
        preprocess.setNovelId(NOVEL_ID);
        preprocess.setCompletedStage(completedStage);
        return preprocess;
    }
}

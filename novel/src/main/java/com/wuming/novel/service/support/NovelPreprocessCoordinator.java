package com.wuming.novel.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.NovelPreprocess;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.enums.NovelPreprocessStage;
import com.wuming.novel.domain.enums.NovelPreprocessStatus;
import com.wuming.novel.infrastructure.mapper.NovelPreprocessMapper;
import com.wuming.novel.pipeline.lock.NovelPreprocessLock;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.INovelPassageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 协调同一小说的章节与 Passage 公共预处理。
 *
 * <p>job 仍保有自身阶段与进度；此协调器只决定该阶段实际构建还是复用既有产物。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovelPreprocessCoordinator {
    private static final Duration WAIT_TIMEOUT = Duration.ofMinutes(30);
    private static final long WAIT_INTERVAL_MILLIS = 500L;

    private final NovelPreprocessMapper preprocessMapper;
    private final NovelPreprocessLock preprocessLock;
    private final IChapterService chapterService;
    private final INovelPassageService novelPassageService;

    /**
     * 在共享预处理已完成时直接复用，否则由唯一持锁者执行该阶段。
     *
     * @param novelId 小说主键
     * @param stage 当前公共预处理阶段
     * @param work 真正执行构建的任务
     */
    public void execute(Long novelId, NovelPreprocessStage stage, StageWork work) {
        Lease lease = acquire(novelId, stage);
        if (!lease.owner()) {
            log.info("复用小说预处理产物，novelId: {}, stage: {}", novelId, stage);
            return;
        }
        try {
            work.run();
            markSucceeded(lease.preprocess(), stage);
            log.info("小说预处理阶段完成，novelId: {}, stage: {}", novelId, stage);
        } catch (RuntimeException exception) {
            markFailed(lease.preprocess(), stage, exception);
            throw exception;
        } finally {
            preprocessLock.release(novelId, lease.token());
        }
    }

    /** 取得阶段生产权；被其他 job 占用时等待其完成或失败。 */
    private Lease acquire(Long novelId, NovelPreprocessStage stage) {
        long deadline = System.nanoTime() + WAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            NovelPreprocess preprocess = getOrCreate(novelId);
            if (completed(preprocess, stage)) {
                return Lease.reused(preprocess);
            }

            String token = preprocessLock.tryLock(novelId);
            if (token == null) {
                awaitOtherProducer(novelId, stage);
                continue;
            }

            NovelPreprocess lockedPreprocess = getOrCreate(novelId);
            if (completed(lockedPreprocess, stage)) {
                preprocessLock.release(novelId, token);
                return Lease.reused(lockedPreprocess);
            }
            if (!canStart(lockedPreprocess, stage)) {
                preprocessLock.release(novelId, token);
                awaitOtherProducer(novelId, stage);
                continue;
            }
            markRunning(lockedPreprocess, stage);
            return Lease.owner(lockedPreprocess, token);
        }
        throw new IllegalStateException("等待小说预处理超时，novelId: " + novelId + ", stage: " + stage);
    }

    /** 休眠一个短周期，避免多个 job 在共享锁上自旋。 */
    private void awaitOtherProducer(Long novelId, NovelPreprocessStage stage) {
        try {
            Thread.sleep(WAIT_INTERVAL_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待小说预处理被中断，novelId: " + novelId + ", stage: " + stage, exception);
        }
    }

    /** 仅允许按章节切分、Passage构建的顺序生成共享产物。 */
    private boolean canStart(NovelPreprocess preprocess, NovelPreprocessStage stage) {
        NovelPreprocessStage completedStage = preprocess.getCompletedStage();
        return switch (stage) {
            case CHAPTER_SPLIT -> completedStage == NovelPreprocessStage.NONE;
            case PASSAGE_BUILD -> completedStage == NovelPreprocessStage.CHAPTER_SPLIT;
            case NONE -> false;
        };
    }

    private boolean completed(NovelPreprocess preprocess, NovelPreprocessStage stage) {
        return preprocess.getCompletedStage() != null && preprocess.getCompletedStage().covers(stage);
    }

    /** 读取小说状态；并发首建发生唯一键冲突时重新读取已有记录。 */
    private NovelPreprocess getOrCreate(Long novelId) {
        NovelPreprocess existing = findByNovelId(novelId);
        if (existing != null) {
            return existing;
        }
        NovelPreprocess created = new NovelPreprocess();
        created.setNovelId(novelId);
        created.setStatus(NovelPreprocessStatus.PENDING);
        created.setCompletedStage(NovelPreprocessStage.NONE);
        created.setChapterCount(0);
        created.setPassageCount(0);
        try {
            preprocessMapper.insert(created);
            return created;
        } catch (DuplicateKeyException ignored) {
            return findByNovelId(novelId);
        }
    }

    private NovelPreprocess findByNovelId(Long novelId) {
        return preprocessMapper.selectOne(new LambdaQueryWrapper<NovelPreprocess>()
                .eq(NovelPreprocess::getNovelId, novelId));
    }

    /** 将锁持有者的阶段声明为运行中，并清空上一次失败信息。 */
    private void markRunning(NovelPreprocess preprocess, NovelPreprocessStage stage) {
        NovelPreprocess update = new NovelPreprocess();
        update.setId(preprocess.getId());
        update.setStatus(NovelPreprocessStatus.RUNNING);
        update.setCurrentStage(stage);
        update.setFailureReason(null);
        update.setStartedTime(LocalDateTime.now());
        preprocessMapper.updateById(update);
        preprocess.setStatus(NovelPreprocessStatus.RUNNING);
        preprocess.setCurrentStage(stage);
    }

    /** 记录成功产物数量，并在 Passage 完成时将快照置为 READY。 */
    private void markSucceeded(NovelPreprocess preprocess, NovelPreprocessStage stage) {
        Long novelId = preprocess.getNovelId();
        NovelPreprocess update = new NovelPreprocess();
        update.setId(preprocess.getId());
        update.setCurrentStage(null);
        update.setCompletedStage(stage);
        update.setFailureReason(null);
        update.setChapterCount(Math.toIntExact(chapterService.count(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getNovelId, novelId))));
        if (stage == NovelPreprocessStage.PASSAGE_BUILD) {
            update.setStatus(NovelPreprocessStatus.READY);
            update.setPassageCount(Math.toIntExact(novelPassageService.count(new LambdaQueryWrapper<NovelPassage>()
                    .eq(NovelPassage::getNovelId, novelId))));
            update.setCompletedTime(LocalDateTime.now());
        } else {
            update.setStatus(NovelPreprocessStatus.PENDING);
        }
        preprocessMapper.updateById(update);
    }

    /** 记录失败阶段，后续 job 会从最后未完成阶段重新尝试。 */
    private void markFailed(NovelPreprocess preprocess, NovelPreprocessStage stage, RuntimeException exception) {
        NovelPreprocess update = new NovelPreprocess();
        update.setId(preprocess.getId());
        update.setStatus(NovelPreprocessStatus.FAILED);
        update.setCurrentStage(stage);
        update.setFailureReason(exception.getMessage());
        preprocessMapper.updateById(update);
        log.warn("小说预处理阶段失败，novelId: {}, stage: {}, errorType: {}, errorMessage: {}",
                preprocess.getNovelId(), stage, exception.getClass().getSimpleName(), exception.getMessage());
    }

    /** 小说公共预处理阶段的实际执行回调。 */
    @FunctionalInterface
    public interface StageWork {
        void run();
    }

    private record Lease(NovelPreprocess preprocess, String token, boolean owner) {
        private static Lease owner(NovelPreprocess preprocess, String token) {
            return new Lease(preprocess, token, true);
        }

        private static Lease reused(NovelPreprocess preprocess) {
            return new Lease(preprocess, null, false);
        }
    }
}

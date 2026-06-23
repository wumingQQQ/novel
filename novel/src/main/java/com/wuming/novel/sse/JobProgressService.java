package com.wuming.novel.sse;

import com.wuming.novel.domain.enums.JobStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Service
public class JobProgressService {
    private static final long SSE_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    // 存储所有job的进度对象
    private final Map<Long, JobProgress> jobProgressMap = new ConcurrentHashMap<>();
    // 订阅关系, 暂时只做单对单
    private final Map<Long, SseEmitter> subscribers = new ConcurrentHashMap<>();

    public JobProgress initJob(Long jobId) {
        JobProgress progress = JobProgress.initJob(jobId);
        jobProgressMap.put(jobId, progress);
        pushUpdate(jobId, progress);
        return progress;
    }

    public void startJob(Long jobId) {
        updateAndPush(jobId, JobProgress::startJob);
    }

    public void setStageTotalItems(Long jobId, JobStage stage, int totalItems) {
        updateAndPush(jobId, progress -> progress.getStage(stage).setTotalItems(totalItems));
    }

    public void startStage(Long jobId, JobStage stage, String message) {
        updateAndPush(jobId, progress -> progress.startStage(stage, message));
    }

    public void completeStage(Long jobId, JobStage stage, String message) {
        updateAndPush(jobId, progress -> progress.completeStage(stage, message));
    }

    public void failStage(Long jobId, JobStage stage, String message) {
        updateAndPush(jobId, progress -> progress.failStage(stage, message));
    }

    public void recordItemSuccess(Long jobId, JobStage stage) {
        updateAndPush(jobId, progress -> {
            StageProgress stageProgress = progress.getStage(stage);
            stageProgress.recordItemSuccess();
            completeCountedStageIfReady(stageProgress);
            progress.touch();
        });
    }

    public void recordItemFailure(Long jobId, JobStage stage) {
        updateAndPush(jobId, progress -> {
            StageProgress stageProgress = progress.getStage(stage);
            stageProgress.recordItemFailure();
            completeCountedStageIfReady(stageProgress);
            progress.touch();
        });
    }

    // 带计数阶段的完成
    public void finishCountedStage(Long jobId, JobStage stage, String successMessage, String failedMessage) {
        updateAndPush(jobId, progress -> {
            StageProgress stageProgress = progress.getStage(stage);
            if(stageProgress.hasFailedItems()){
                stageProgress.fail(failedMessage);
                progress.touch();
                return;
            }
            stageProgress.complete(successMessage);
            progress.touch();
        });
    }

    public void completeJob(Long jobId){
        updateAndPush(jobId, JobProgress::completeJob);
        closeSubscriber(jobId);
    }

    public void failJob(Long jobId, String message){
        updateAndPush(jobId, progress -> progress.failJob(message));
        closeSubscriber(jobId);
    }

    public JobProgress getProgress(Long jobId) {
        return jobProgressMap.get(jobId);
    }

    public JobProgress getOrInitProgress(Long jobId) {
        return jobProgressMap.computeIfAbsent(jobId, JobProgress::initJob);
    }

    public void clear(Long jobId) {
        closeSubscriber(jobId);
        subscribers.remove(jobId);
        jobProgressMap.remove(jobId);
    }

    public void heartbeat(Long jobId) {
        JobProgress progress = jobProgressMap.get(jobId);
        if(progress != null){
            pushUpdate(jobId, progress);
        }
    }

    public SseEmitter subscribe(Long jobId){
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        replaceSubscriber(jobId, emitter);

        JobProgress progress = jobProgressMap.get(jobId);
        if(progress != null){
            send(jobId, emitter, progress);
        }

        return emitter;
    }

    private void completeCountedStageIfReady(StageProgress stage) {
        if(!stage.isAllItemsFinished()){
            return;
        }

        if(stage.hasFailedItems()){
            stage.fail(stage.getMessage());
            return;
        }
        stage.complete(stage.getMessage());
    }

    private void updateAndPush(Long jobId, Consumer<JobProgress> updater) {
        JobProgress progress = jobProgressMap.get(jobId);
        if(progress == null){
            log.debug("job: {} 进度对象不存在，忽略进度更新", jobId);
            return;
        }
        updater.accept(progress);
        pushUpdate(jobId, progress);
    }

    private void pushUpdate(Long jobId, JobProgress progress){
        SseEmitter emitter = subscribers.get(jobId);
        if(emitter != null){
            send(jobId, emitter, progress);
        }
    }

    private void replaceSubscriber(Long jobId, SseEmitter emitter) {
        SseEmitter oldEmitter = subscribers.put(jobId, emitter);
        if(oldEmitter != null){
            oldEmitter.complete();
        }
        emitter.onCompletion(() -> subscribers.remove(jobId, emitter));
        emitter.onTimeout(() -> subscribers.remove(jobId, emitter));
        emitter.onError(e -> {
            log.error("job: {} SSE订阅异常", jobId, e);
            subscribers.remove(jobId, emitter);
        });
    }

    private void send(Long jobId, SseEmitter emitter, JobProgress progress) {
        try{
            emitter.send(SseEmitter.event().name("progress").data(progress));
        } catch (IOException | IllegalStateException e) {
            subscribers.remove(jobId, emitter);
            emitter.completeWithError(e);
        }
    }

    private void closeSubscriber(Long jobId){
        SseEmitter emitter = subscribers.get(jobId);
        if(emitter != null){
            try{
                emitter.complete();
            }
            catch (IllegalStateException e) {
                emitter.completeWithError(e);
            }
            finally {
                subscribers.remove(jobId);
            }
        }
    }
}

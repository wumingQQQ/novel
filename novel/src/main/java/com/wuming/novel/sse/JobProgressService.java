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
    private final JobProgressStore jobProgressStore;

    public JobProgressService(JobProgressStore jobProgressStore) {
        this.jobProgressStore = jobProgressStore;
    }

    /**
     * 初始化任务进度，并立即写入本地缓存、Redis 和当前 SSE 订阅。
     */
    public void initJob(Long jobId) {
        JobProgress progress = JobProgress.initJob(jobId);
        jobProgressMap.put(jobId, progress);
        jobProgressStore.save(progress);
        pushUpdate(jobId, progress);
    }

    public void startJob(Long jobId) {
        updateAndPush(jobId, JobProgress::startJob);
    }

    /**
     * 设置计数阶段的子任务总数，并重置该阶段的成功和失败计数。
     */
    public void setStageTotalItems(Long jobId, JobStage stage, int totalItems) {
        updateAndPush(jobId, progress -> progress.getStage(stage).resetTotalItems(totalItems));
    }

    /**
     * 设置计数阶段的完整子任务计数，用于断点续跑和失败项重试时恢复展示状态。
     */
    public void setStageItemCounts(Long jobId, JobStage stage, int totalItems, int successItems, int failedItems) {
        updateAndPush(jobId, progress -> progress.getStage(stage)
                .resetItemCounts(totalItems, successItems, failedItems));
    }

    public void startStage(Long jobId, JobStage stage, String message) {
        updateAndPush(jobId, progress -> progress.startStage(stage, message));
    }

    public void completeStage(Long jobId, JobStage stage, String message) {
        updateAndPush(jobId, progress -> progress.completeStage(stage, message));
    }

    /**
     * 标记已跳过阶段完成；计数阶段没有可恢复子项明细时展示为0/0完成。
     */
    public void completeSkippedStage(Long jobId, JobStage stage, String message) {
        updateAndPush(jobId, progress -> {
            StageProgress stageProgress = progress.getStage(stage);
            if (stageProgress.isCounted()) {
                stageProgress.resetItemCounts(0, 0, 0);
            }
            stageProgress.complete(message);
            progress.touch();
        });
    }

    public void failStage(Long jobId, JobStage stage, String message) {
        updateAndPush(jobId, progress -> progress.failStage(stage, message));
    }

    /**
     * 记录计数阶段的子任务成功，并在全部子任务结束时自动完成阶段。
     */
    public void recordItemSuccess(Long jobId, JobStage stage) {
        updateAndPush(jobId, progress -> {
            StageProgress stageProgress = progress.getStage(stage);
            stageProgress.recordItemSuccess();
            progress.touch();
        });
    }

    /**
     * 记录计数阶段的子任务失败，并在全部子任务结束时自动结束阶段。
     */
    public void recordItemFailure(Long jobId, JobStage stage) {
        updateAndPush(jobId, progress -> {
            StageProgress stageProgress = progress.getStage(stage);
            stageProgress.recordItemFailure();
            progress.touch();
        });
    }

    public void completeJob(Long jobId){
        updateAndPush(jobId, JobProgress::completeJob);
        closeSubscriber(jobId);
        evictLocalProgress(jobId);
    }

    public void failJob(Long jobId, String message){
        updateAndPush(jobId, progress -> progress.failJob(message));
        closeSubscriber(jobId);
        evictLocalProgress(jobId);
    }

    /**
     * 优先读取本地缓存；本地没有时从 Redis 恢复，避免进程重启后丢失进度。
     */
    public JobProgress getProgress(Long jobId) {
        JobProgress progress = jobProgressMap.get(jobId);
        if (progress != null) {
            return progress;
        }

        progress = jobProgressStore.get(jobId);
        if (progress != null) {
            jobProgressMap.put(jobId, progress);
            return progress;
        }
        return null;
    }

    /**
     * 获取已有进度；如果本地和 Redis 都没有，则创建一份初始进度。
     */
    public JobProgress getOrInitProgress(Long jobId) {
        JobProgress progress = getProgress(jobId);
        if (progress != null) {
            return progress;
        }
        progress = JobProgress.initJob(jobId);
        jobProgressMap.put(jobId, progress);
        jobProgressStore.save(progress);
        return progress;
    }

    /**
     * 清理任务进度、SSE 订阅和 Redis 持久化数据。
     */
    public void clear(Long jobId) {
        closeSubscriber(jobId);
        subscribers.remove(jobId);
        jobProgressMap.remove(jobId);
        jobProgressStore.delete(jobId);
    }

    public void heartbeat(Long jobId) {
        JobProgress progress = jobProgressMap.get(jobId);
        if(progress != null){
            pushUpdate(jobId, progress);
        }
    }

    /**
     * 建立单 job 的 SSE 订阅；新订阅会替换旧订阅。
     */
    public SseEmitter subscribe(Long jobId){
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        replaceSubscriber(jobId, emitter);

        JobProgress progress = getOrInitProgress(jobId);
        send(jobId, emitter, progress);

        return emitter;
    }

    /**
     * 对本地进度执行变更，并同步保存 Redis 与推送 SSE。
     */
    private void updateAndPush(Long jobId, Consumer<JobProgress> updater) {
        JobProgress progress = jobProgressMap.get(jobId);
        if(progress == null){
            log.debug("job: {} 进度对象不存在，忽略进度更新", jobId);
            return;
        }
        synchronized (progress) {
            updater.accept(progress);
            jobProgressStore.save(progress);
        }
        pushUpdate(jobId, progress);
    }

    /**
     * 如果当前 job 存在 SSE 订阅，则推送最新进度。
     */
    private void pushUpdate(Long jobId, JobProgress progress){
        SseEmitter emitter = subscribers.get(jobId);
        if(emitter != null){
            send(jobId, emitter, progress);
        }
    }

    /**
     * 替换当前 job 的 SSE 订阅，并注册连接关闭后的清理逻辑。
     */
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

    /**
     * 向 SSE 客户端发送一次 progress 事件；发送失败时移除订阅。
     */
    private void send(Long jobId, SseEmitter emitter, JobProgress progress) {
        try{
            emitter.send(SseEmitter.event().name("progress").data(progress));
        } catch (IOException | IllegalStateException e) {
            subscribers.remove(jobId, emitter);
            emitter.completeWithError(e);
        }
    }

    /**
     * 任务结束时关闭 SSE 连接，并移除订阅关系。
     */
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

    /**
     * 任务结束后仅清理本地进度缓存；Redis进度仍按TTL保留供后续查询。
     */
    private void evictLocalProgress(Long jobId) {
        jobProgressMap.remove(jobId);
    }
}

package com.wuming.novel.sse;

import com.wuming.novel.domain.enums.JobStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class JobProgress {
    private Long jobId;
    private JobStage currentStage;
    private TaskState state;
    private Instant startTime;
    private Instant updateTime;
    private Instant endTime;
    private List<StageProgress> stages;

    public static JobProgress initJob(Long jobId){
        List<StageProgress> stages = List.of(
                StageProgress.simple(JobStage.CHAPTER_SPLIT, "章节切分"),
                StageProgress.simple(JobStage.LAYER_SPLIT, "层级划分"),
                StageProgress.counted(JobStage.SCENE_SPLIT, "场景切分"),
                StageProgress.counted(JobStage.POOL_CLASSIFY, "池化分类"),
                StageProgress.counted(JobStage.EVIDENCE_EXTRACT, "证据提取"),
                StageProgress.simple(JobStage.PROFILE_AGGREGATION, "画像聚合"),
                StageProgress.simple(JobStage.PROFILE_DETAIL_ENHANCE, "画像细节增强")
        );

        return JobProgress.builder()
                .jobId(jobId)
                .currentStage(JobStage.PENDING)
                .state(TaskState.PENDING)
                .startTime(Instant.now())
                .updateTime(Instant.now())
                .stages(stages)
                .build();
    }

    public void startJob(){
        if(state == TaskState.PENDING){
            markRunning();
            touch();
        }
    }

    private void markRunning(){
        state = TaskState.RUNNING;
        updateTime = Instant.now();
    }

    public StageProgress getStage(JobStage stage) {
        return stages.stream()
                .filter(s -> s.getJobStage() == stage)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stage not found: " + stage));
    }

    public void startStage(JobStage stage, String message) {
        this.currentStage = stage;
        getStage(stage).start(message);
        touch();
    }

    public void completeStage(JobStage stage, String message) {
        getStage(stage).complete(message);
        touch();
    }

    public void failStage(JobStage stage, String message) {
        getStage(stage).fail(message);
        this.state = TaskState.FAILED;
        touch();
    }

    public void completeJob() {
        if (currentStage != JobStage.PENDING && currentStage != JobStage.COMPLETE) {
            StageProgress stage = getStage(currentStage);
            if (stage.getState() == TaskState.RUNNING) {
                stage.complete("阶段完成");
            }
        }
        this.currentStage = JobStage.COMPLETE;
        this.state = TaskState.DONE;
        touch();
        this.endTime = updateTime;
    }

    public void failJob(String message) {
        if (currentStage != JobStage.PENDING && currentStage != JobStage.COMPLETE) {
            StageProgress stage = getStage(currentStage);
            if (stage.getState() == TaskState.RUNNING) {
                stage.fail(message);
            }
        }
        this.state = TaskState.FAILED;
        touch();
        endTime = updateTime;
    }

    public void touch() {
        updateTime = Instant.now();
    }
}

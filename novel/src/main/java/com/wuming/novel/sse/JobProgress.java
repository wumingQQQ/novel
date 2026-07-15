package com.wuming.novel.sse;

import com.wuming.novel.domain.enums.JobStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobProgress {
    private Long jobId;
    private JobStage currentStage;
    private TaskState state;
    private Instant startTime;
    private Instant updateTime;
    private Instant endTime;
    private List<StageProgress> stages;

    /**
     * 创建任务初始进度结构，包含所有固定阶段及其展示模式。
     */
    public static JobProgress initJob(Long jobId){
        List<StageProgress> stages = List.of(
                StageProgress.simple(JobStage.CHAPTER_SPLIT, "章节切分"),
                StageProgress.counted(JobStage.PASSAGE_BUILD, "Passage构建"),
                StageProgress.counted(JobStage.ROLE_EXAMPLE_BUILD, "角色样本构建"),
                StageProgress.counted(JobStage.REACTION_RULE_BUILD, "反应规则构建"),
                StageProgress.simple(JobStage.ROLE_PROFILE_BUILD, "角色画像构建")
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

    /**
     * 将任务从待运行标记为运行中；重复调用不会覆盖已有运行状态。
     */
    public void startJob(){
        if(state == TaskState.PENDING || state == TaskState.FAILED){
            markRunning();
            endTime = null;
            touch();
        }
    }

    private void markRunning(){
        state = TaskState.RUNNING;
        updateTime = Instant.now();
    }

    /**
     * 根据阶段枚举获取对应阶段进度，未配置阶段会直接抛出异常。
     */
    public StageProgress getStage(JobStage stage) {
        return stages.stream()
                .filter(s -> s.getJobStage() == stage)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Stage not found: " + stage));
    }

    /**
     * 标记当前阶段开始，并推进任务的 currentStage。
     */
    public void startStage(JobStage stage, String message) {
        this.currentStage = stage;
        getStage(stage).start(message);
        touch();
    }

    /**
     * 标记指定阶段完成，不改变任务整体完成状态。
     */
    public void completeStage(JobStage stage, String message) {
        getStage(stage).complete(message);
        touch();
    }

    /**
     * 标记指定阶段失败，并同步将任务整体状态置为失败。
     */
    public void failStage(JobStage stage, String message) {
        getStage(stage).fail(message);
        this.state = TaskState.FAILED;
        touch();
    }

    /**
     * 标记整个任务完成；如果当前阶段仍在运行，会先补齐阶段完成状态。
     */
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

    /**
     * 标记整个任务失败；如果当前阶段仍在运行，会同步标记该阶段失败。
     */
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

    /**
     * 刷新任务更新时间，供阶段和子任务状态变更后统一调用。
     */
    public void touch() {
        updateTime = Instant.now();
    }
}

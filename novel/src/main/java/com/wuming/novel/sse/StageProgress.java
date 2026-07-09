package com.wuming.novel.sse;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wuming.novel.domain.enums.JobStage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StageProgress {
    private JobStage jobStage;
    private StageMode stageMode;
    private TaskState state;
    @Setter(AccessLevel.NONE)
    private Integer totalItems;
    private @Getter(AccessLevel.NONE)
    AtomicInteger successItems;
    private @Getter(AccessLevel.NONE)
    AtomicInteger failedItems;
    private String message;
    private Instant startTime;
    private Instant endTime;

    /**
     * 将内部计数器以普通数字形式输出，便于前端展示和 Redis JSON 持久化。
     */
    @JsonGetter("successItems")
    public int getSuccessItemValue() {
        return successItems == null ? 0 : successItems.get();
    }

    /**
     * 将内部失败计数器以普通数字形式输出，便于前端展示和 Redis JSON 持久化。
     */
    @JsonGetter("failedItems")
    public int getFailedItemValue() {
        return failedItems == null ? 0 : failedItems.get();
    }

    /**
     * 创建只关心开始和完成状态的阶段进度。
     */
    public static StageProgress simple(
            JobStage stage, String message
    ) {
        return new StageProgress(
                stage, StageMode.SIMPLE, TaskState.PENDING,
                null, null, null,
                message, null, null
        );
    }

    /**
     * 创建需要统计子任务总数、成功数和失败数的阶段进度。
     */
    public static StageProgress counted(
            JobStage stage, String message
    ) {
        return new StageProgress(
                stage, StageMode.COUNTED, TaskState.PENDING,
                -1, new AtomicInteger(), new AtomicInteger(),
                message, null, null
        );
    }

    /**
     * 标记阶段开始；重复开始时保留第一次开始时间。
     */
    public void start(String message) {
        if (startTime == null) {
            startTime = Instant.now();
        }
        this.state = TaskState.RUNNING;
        this.message = message;
        this.endTime = null;
    }

    /**
     * 标记阶段成功完成，并记录完成时间。
     */
    public void complete(String message) {
        this.state = TaskState.DONE;
        this.message = message;
        this.endTime = Instant.now();
    }

    /**
     * 标记阶段失败，并记录失败时间。
     */
    public void fail(String message) {
        this.state = TaskState.FAILED;
        this.message = message;
        this.endTime = Instant.now();
    }

    /**
     * 记录一个子任务成功；只允许计数阶段调用。
     */
    public void recordItemSuccess(){
        assertCountedStage();
        initCountersIfNecessary();
        successItems.incrementAndGet();
    }

    /**
     * 记录一个子任务失败；只允许计数阶段调用。
     */
    public void recordItemFailure(){
        assertCountedStage();
        initCountersIfNecessary();
        failedItems.incrementAndGet();
    }

    /**
     * 业务侧设置子任务总数，并同步清零成功和失败计数。
     */
    public void resetTotalItems(int totalItems) {
        if (!isCounted()) {
            throw new IllegalStateException("只有计数阶段可以更新子任务数量: " + jobStage);
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("totalItems不能小于0");
        }
        this.totalItems = totalItems;
        this.successItems = new AtomicInteger();
        this.failedItems = new AtomicInteger();
    }

    /**
     * 设置计数阶段的完整计数快照，用于断点续跑和失败项重试时恢复展示状态。
     */
    public void resetItemCounts(int totalItems, int successItems, int failedItems) {
        if (!isCounted()) {
            throw new IllegalStateException("只有计数阶段可以更新子任务数量: " + jobStage);
        }
        if (totalItems < 0 || successItems < 0 || failedItems < 0) {
            throw new IllegalArgumentException("子任务计数不能小于0");
        }
        if (successItems + failedItems > totalItems) {
            throw new IllegalArgumentException("成功和失败子任务数不能超过总数");
        }
        this.totalItems = totalItems;
        this.successItems = new AtomicInteger(successItems);
        this.failedItems = new AtomicInteger(failedItems);
    }

    /**
     * 专供 Redis 反序列化恢复字段值，不在业务流程中直接调用。
     */
    public void setSuccessItems(int successItems) {
        this.successItems = new AtomicInteger(successItems);
    }

    /**
     * 专供 Redis 反序列化恢复字段值，不在业务流程中直接调用。
     */
    public void setFailedItems(int failedItems) {
        this.failedItems = new AtomicInteger(failedItems);
    }

    /**
     * 判断当前阶段是否需要统计子任务进度。
     */
    @JsonIgnore
    public boolean isCounted(){
        return stageMode == StageMode.COUNTED;
    }

    /**
     * 判断计数阶段的所有子任务是否都已经成功或失败。
     */
    @JsonIgnore
    public boolean isAllItemsFinished() {
        initCountersIfNecessary();
        return isCounted()
                && totalItems != null
                && totalItems >= 0
                && getSuccessItemValue() + getFailedItemValue() >= totalItems;
    }

    /**
     * 判断计数阶段是否存在失败子任务。
     */
    @JsonIgnore
    public boolean hasFailedItems() {
        initCountersIfNecessary();
        return isCounted() && getFailedItemValue() > 0;
    }

    /**
     * 防止简单阶段误调用子任务计数相关方法。
     */
    private void assertCountedStage() {
        if (!isCounted()) {
            throw new IllegalStateException("只有计数阶段可以更新子任务数量: " + jobStage);
        }
    }

    /**
     * Redis 恢复或旧数据缺少计数器时，按需补齐内部 AtomicInteger。
     */
    private void initCountersIfNecessary() {
        if (!isCounted()) {
            return;
        }
        if (successItems == null) {
            successItems = new AtomicInteger();
        }
        if (failedItems == null) {
            failedItems = new AtomicInteger();
        }
    }
}

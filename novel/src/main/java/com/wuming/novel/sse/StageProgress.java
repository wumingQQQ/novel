package com.wuming.novel.sse;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.wuming.novel.domain.enums.JobStage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@AllArgsConstructor
public class StageProgress {
    private JobStage jobStage;
    private StageMode stageMode;
    private TaskState state;
    private Integer totalItems;
    private @Getter(AccessLevel.NONE)
    AtomicInteger successItems;
    private @Getter(AccessLevel.NONE)
    AtomicInteger failedItems;
    private String message;
    private Instant startTime;
    private Instant endTime;

    @JsonGetter("successItems")
    public int getSuccessItemValue() {
        return successItems == null ? 0 : successItems.get();
    }

    @JsonGetter("failedItems")
    public int getFailedItemValue() {
        return failedItems == null ? 0 : failedItems.get();
    }

    public static StageProgress simple(
            JobStage stage, String message
    ) {
        return new StageProgress(
                stage, StageMode.SIMPLE, TaskState.PENDING,
                null, null, null,
                message, null, null
        );
    }

    public static StageProgress counted(
            JobStage stage, String message
    ) {
        return new StageProgress(
                stage, StageMode.COUNTED, TaskState.PENDING,
                -1, new AtomicInteger(), new AtomicInteger(),
                message, null, null
        );
    }

    public void start(String message) {
        if (startTime == null) {
            startTime = Instant.now();
        }
        this.state = TaskState.RUNNING;
        this.message = message;
        this.endTime = null;
    }

    public void complete(String message) {
        this.state = TaskState.DONE;
        this.message = message;
        this.endTime = Instant.now();
    }

    public void fail(String message) {
        this.state = TaskState.FAILED;
        this.message = message;
        this.endTime = Instant.now();
    }

    public void recordItemSuccess(){
        assertCountedStage();
        successItems.incrementAndGet();
    }

    public void recordItemFailure(){
        assertCountedStage();
        failedItems.incrementAndGet();
    }

    public void setTotalItems(int totalItems) {
        assertCountedStage();
        if (totalItems < 0) {
            throw new IllegalArgumentException("totalItems不能小于0");
        }
        this.totalItems = totalItems;
    }

    public boolean isCounted(){
        return stageMode == StageMode.COUNTED;
    }

    public boolean isAllItemsFinished() {
        return isCounted()
                && totalItems != null
                && totalItems >= 0
                && getSuccessItemValue() + getFailedItemValue() >= totalItems;
    }

    public boolean hasFailedItems() {
        return isCounted() && getFailedItemValue() > 0;
    }

    private void assertCountedStage() {
        if (!isCounted()) {
            throw new IllegalStateException("只有计数阶段可以更新子任务数量: " + jobStage);
        }
    }
}

package com.wuming.novel.message.jobdone;

import com.wuming.novel.message.CompleteEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * job完成或失败时发生的事件实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JobFinishEvent extends CompleteEvent {
    private JobFinishStatus status;
    private String message;


     public enum JobFinishStatus{
        SUCCESS,
        FAILED,
    }
}

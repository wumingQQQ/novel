package com.wuming.novel.domain.dto;

import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.domain.enums.JobStatus;

import java.time.LocalDateTime;

/** 当前用户任务中心的列表项。 */
public record MyJobResponse(Long id, Long novelId, String novelName, String protagonistName, String targetName,
                            JobStage stage, JobStatus status, String failureReason,
                            LocalDateTime createTime, LocalDateTime startedTime, LocalDateTime finishedTime) {
}

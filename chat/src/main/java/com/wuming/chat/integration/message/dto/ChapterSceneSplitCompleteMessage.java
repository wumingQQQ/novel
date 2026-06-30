package com.wuming.chat.integration.message.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChapterSceneSplitCompleteMessage {
    private Long jobId;
    private Long novelId;
    private Long chapterId;
    private Integer chapterSequence;
    private Integer sceneCount;
    private LocalDateTime occurTime;
}


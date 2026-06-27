package com.wuming.novel.message;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CompleteEvent {
    private Long jobId;
    private Long novelId;
    private LocalDateTime occurTime;

    public CompleteEvent(){
        this.occurTime = LocalDateTime.now();
    }
}

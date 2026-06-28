package com.wuming.user.message.eventdto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobFinishedMessage {
    private Long jobId;
    private Long novelId;
    private Long userId;
    private String status;
    private String failReason;
    private LocalDateTime occurTime;

    public boolean success(){
        return "SUCCESS".equals(status);
    }

    public boolean fail(){
        return "FAILED".equals(status);
    }
}

package com.wuming.novel.message.jobdone;

import com.wuming.novel.message.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobCompleteEventPublisher implements EventPublisher<JobFinishEvent> {
    @Override
    public void publish(JobFinishEvent event) {
        log.info(
                "任务已{}，jobId: {}, novelId: {}",
                event.getStatus(),
                event.getJobId(),
                event.getNovelId()
        );
    }
}

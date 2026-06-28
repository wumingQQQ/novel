package com.wuming.novel.message.jobdone;

import com.wuming.novel.message.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "false", matchIfMissing = true)
public class JobFinishEventPublisher implements EventPublisher<JobFinishEvent> {
    @Override
    public void publish(JobFinishEvent event) {
        log.info(
                "任务已{}，jobId: {}, userId: {}, novelId: {}",
                event.getStatus(),
                event.getJobId(),
                event.getUserId(),
                event.getNovelId()
        );
    }
}

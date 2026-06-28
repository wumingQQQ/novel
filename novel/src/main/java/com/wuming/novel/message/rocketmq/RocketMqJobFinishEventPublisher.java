package com.wuming.novel.message.rocketmq;

import com.wuming.novel.message.EventPublisher;
import com.wuming.novel.message.jobdone.JobFinishEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "true")
public class RocketMqJobFinishEventPublisher implements EventPublisher<JobFinishEvent> {
    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMqTagProperties tagProperties;

    @Value("${novel.mq.topic}")
    private String topic;

    @Override
    public void publish(JobFinishEvent event) {
        log.info(
                "任务已{}，jobId: {}, userId: {}, novelId: {}",
                event.getStatus(),
                event.getJobId(),
                event.getUserId(),
                event.getNovelId()
        );
        String destination = topic + ":" + tagProperties.getJobFinished();
        rocketMQTemplate.convertAndSend(
                destination,
                event
        );
    }
}

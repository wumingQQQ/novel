package com.wuming.user.message.eventlistener;

import com.wuming.user.message.eventdto.JobFinishedMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${user.mq.topic}",
        selectorExpression = "${user.mq.tags.job-finished}",
        consumerGroup = "user-job-consumer-group"
)
public class JobFinishedEventListener implements RocketMQListener<JobFinishedMessage> {
    @Override
    public void onMessage(JobFinishedMessage message) {
        log.info(message.toString());
    }
}

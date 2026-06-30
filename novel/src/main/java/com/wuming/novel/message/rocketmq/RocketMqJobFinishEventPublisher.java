package com.wuming.novel.message.rocketmq;

import com.wuming.novel.message.EventPublisher;
import com.wuming.novel.message.jobdone.JobFinishEvent;
import com.wuming.novel.infrastructure.observability.TraceContext;
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

    /**
     * 发送任务完成或失败事件，供下游用户通知模块消费。
     *
     * @param event 任务完成事件
     */
    @Override
    public void publish(JobFinishEvent event) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(event.getJobId());
             TraceContext.MdcScope ignoredUser = TraceContext.putUserId(event.getUserId());
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(event.getNovelId())) {
            String destination = topic + ":" + tagProperties.getJobFinished();
            log.info("开始发送任务完成事件，destination: {}, status: {}",
                    destination, event.getStatus());
            rocketMQTemplate.convertAndSend(destination, event);
            log.info("任务完成事件发送成功，destination: {}, status: {}",
                    destination, event.getStatus());
        }
    }
}

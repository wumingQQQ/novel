package com.wuming.novel.integration.message.rocketmq;

import com.wuming.common.messaging.MqDestinations;
import com.wuming.novel.integration.message.EventPublisher;
import com.wuming.novel.integration.message.jobdone.JobFinishEvent;
import com.wuming.novel.infrastructure.observability.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "novel.mq.enabled", havingValue = "true")
public class RocketMqJobFinishEventPublisher implements EventPublisher<JobFinishEvent> {
    private final RocketMQTemplate rocketMQTemplate;

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
            log.info("开始发送任务完成事件，destination: {}, status: {}",
                    MqDestinations.JOB_ENDED, event.getStatus());
            rocketMQTemplate.convertAndSend(MqDestinations.JOB_ENDED, event);
            log.info("任务完成事件发送成功，destination: {}, status: {}",
                    MqDestinations.JOB_ENDED, event.getStatus());
        }
    }
}

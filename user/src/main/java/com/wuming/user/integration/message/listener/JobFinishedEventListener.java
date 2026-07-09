package com.wuming.user.integration.message.listener;

import com.wuming.common.messaging.MqDestinations;
import com.wuming.user.integration.message.dto.JobFinishedMessage;
import com.wuming.user.infrastructure.observability.TraceContext;
import com.wuming.user.service.JobFinishedNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqDestinations.NOVEL_EVENTS_TOPIC,
        selectorExpression = MqDestinations.JOB_ENDED_TAG,
        consumerGroup = "user-job-consumer-group"
)
public class JobFinishedEventListener implements RocketMQListener<JobFinishedMessage> {
    private final JobFinishedNotificationService notificationService;

    /**
     * 消费小说任务完成消息，后续可在这里触发用户通知或邮件发送。
     *
     * @param message 小说任务完成消息
     */
    @Override
    public void onMessage(JobFinishedMessage message) {
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(message.getUserId())) {
            log.info("收到任务完成消息，jobId: {}, novelId: {}, status: {}, occurTime: {}",
                    message.getJobId(), message.getNovelId(), message.getStatus(),
                    message.getOccurTime());
            if (message.getFailReason() != null && !message.getFailReason().isBlank()) {
                log.debug("任务完成消息包含失败原因，jobId: {}, failReason: {}",
                        message.getJobId(), message.getFailReason());
            }
            notificationService.notify(message);
        }
    }
}

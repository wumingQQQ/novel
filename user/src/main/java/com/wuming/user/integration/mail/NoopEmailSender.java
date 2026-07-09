package com.wuming.user.integration.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 未开启邮件发送时的空实现，避免消息消费链路因缺少邮件配置而启动失败。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "user.mail.enabled", havingValue = "false", matchIfMissing = true)
public class NoopEmailSender implements EmailSender {

    @Override
    public boolean send(EmailMessage message) {
        log.debug("邮件发送未启用，跳过发送，subject: {}", message.subject());
        return false;
    }
}

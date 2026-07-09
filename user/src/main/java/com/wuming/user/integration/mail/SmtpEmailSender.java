package com.wuming.user.integration.mail;

import com.wuming.user.config.UserMailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 基于SMTP的邮件发送实现。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "user.mail.enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {
    private final JavaMailSender mailSender;
    private final UserMailProperties properties;

    @Override
    public boolean send(EmailMessage message) {
        validateConfig();
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    true,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(properties.getFrom().trim());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mimeMessage);
            return true;
        } catch (MessagingException e) {
            throw new IllegalStateException("邮件内容构建失败", e);
        }
    }

    private void validateConfig() {
        if (properties.getFrom() == null || properties.getFrom().isBlank()) {
            throw new IllegalStateException("邮件发件人不能为空");
        }
    }
}

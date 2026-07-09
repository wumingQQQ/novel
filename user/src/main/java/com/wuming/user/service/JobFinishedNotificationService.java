package com.wuming.user.service;

import com.wuming.user.domain.entity.User;
import com.wuming.user.domain.enums.UserStatus;
import com.wuming.user.infrastructure.mapper.UserMapper;
import com.wuming.user.integration.mail.EmailMessage;
import com.wuming.user.integration.mail.EmailSender;
import com.wuming.user.integration.message.dto.JobFinishedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 小说任务完成后的用户通知服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobFinishedNotificationService {
    private final UserMapper userMapper;
    private final EmailSender emailSender;

    /**
     * 根据任务完成消息向任务归属用户发送邮件通知。
     *
     * @param message 任务完成消息
     */
    public void notify(JobFinishedMessage message) {
        User user = userMapper.selectById(message.getUserId());
        if (user == null) {
            log.warn("任务完成邮件通知跳过，用户不存在，userId: {}, jobId: {}",
                    message.getUserId(), message.getJobId());
            return;
        }
        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            log.warn("任务完成邮件通知跳过，用户不可用，userId: {}, jobId: {}, status: {}",
                    message.getUserId(), message.getJobId(), user.getStatus());
            return;
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("任务完成邮件通知跳过，用户邮箱为空，userId: {}, jobId: {}",
                    message.getUserId(), message.getJobId());
            return;
        }

        boolean sent = emailSender.send(toEmailMessage(user.getEmail().trim(), message));
        if (sent) {
            log.info("任务完成邮件通知已发送，userId: {}, jobId: {}, status: {}",
                    message.getUserId(), message.getJobId(), message.getStatus());
        } else {
            log.debug("任务完成邮件通知未发送，userId: {}, jobId: {}, status: {}",
                    message.getUserId(), message.getJobId(), message.getStatus());
        }
    }

    private EmailMessage toEmailMessage(String email, JobFinishedMessage message) {
        String subject = message.success() ? "小说角色构建任务已完成" : "小说角色构建任务失败";
        String text = """
                你的小说角色构建任务状态已更新。
                
                任务ID：%s
                小说ID：%s
                状态：%s
                失败原因：%s
                """.formatted(
                message.getJobId(),
                message.getNovelId(),
                message.getStatus(),
                normalizeFailReason(message.getFailReason())
        );
        String html = """
                <p>你的小说角色构建任务状态已更新。</p>
                <ul>
                    <li>任务ID：%s</li>
                    <li>小说ID：%s</li>
                    <li>状态：%s</li>
                    <li>失败原因：%s</li>
                </ul>
                """.formatted(
                message.getJobId(),
                message.getNovelId(),
                escapeHtml(message.getStatus()),
                escapeHtml(normalizeFailReason(message.getFailReason()))
        );
        return new EmailMessage(email, subject, text, html);
    }

    private String normalizeFailReason(String failReason) {
        return failReason == null || failReason.isBlank() ? "无" : failReason.trim();
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

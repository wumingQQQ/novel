package com.wuming.user.integration.mail;

/**
 * 应用内部邮件发送请求。
 */
public record EmailMessage(
        String to,
        String subject,
        String text,
        String html
) {
}


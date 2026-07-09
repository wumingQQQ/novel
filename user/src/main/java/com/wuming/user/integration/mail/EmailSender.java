package com.wuming.user.integration.mail;

/**
 * 邮件发送端口。
 */
public interface EmailSender {

    boolean send(EmailMessage message);
}

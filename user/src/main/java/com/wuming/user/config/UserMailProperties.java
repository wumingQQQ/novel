package com.wuming.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 用户服务邮件通知配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "user.mail")
public class UserMailProperties {
    private boolean enabled;
    /**
     * 发件人地址，需要使用已在Resend验证过的域名。
     */
    private String from;
}

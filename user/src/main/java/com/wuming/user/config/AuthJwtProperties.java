package com.wuming.user.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.jwt")
public class AuthJwtProperties {
    String issuer;
    String secret;
    long expiresMinutes;
}

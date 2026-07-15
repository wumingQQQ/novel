package com.wuming.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.jwt")
public class JwtProperties {
    private static final int MIN_HS256_SECRET_BYTES = 32;

    private boolean enabled = true;
    private String issuer;
    private String secret;
    private long expiresMinutes = 120;
    private long refreshExpiresDays = 30;
    private String refreshCookieName = "refresh_token";
    private String refreshCookiePath = "/auth";
    private String refreshCookieSameSite = "Lax";
    private boolean refreshCookieSecure = false;

    public String requireIssuer() {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("auth.jwt.issuer must be configured");
        }
        return issuer;
    }

    public SecretKey requireHs256SecretKey() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("auth.jwt.secret must be configured");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_HS256_SECRET_BYTES) {
            throw new IllegalStateException("auth.jwt.secret must be at least 32 bytes for HS256");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}

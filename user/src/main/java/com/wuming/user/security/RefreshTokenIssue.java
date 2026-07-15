package com.wuming.user.security;

import java.time.LocalDateTime;

/**
 * 一次性返回给客户端的刷新令牌签发结果。
 */
public record RefreshTokenIssue(
        String token,
        LocalDateTime expiresTime
) {
}

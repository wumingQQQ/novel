package com.wuming.user.security;

import com.wuming.common.security.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 刷新令牌Cookie管理，避免前端必须把刷新令牌暴露在可被脚本读取的位置。
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieService {
    private final JwtProperties properties;

    /**
     * 将刷新令牌写入HttpOnly Cookie。
     */
    public void write(HttpServletResponse response, String refreshToken, long maxAgeSeconds) {
        ResponseCookie cookie = baseCookie(refreshToken)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 从请求Cookie中解析刷新令牌；不存在时返回null。
     */
    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (properties.getRefreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 清理客户端刷新令牌Cookie。
     */
    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(properties.isRefreshCookieSecure())
                .sameSite(properties.getRefreshCookieSameSite())
                .path(properties.getRefreshCookiePath());
    }
}

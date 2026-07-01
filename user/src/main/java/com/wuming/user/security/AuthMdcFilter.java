package com.wuming.user.security;

import com.wuming.user.infrastructure.observability.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 将已认证用户信息写入日志MDC，便于请求链路日志按userId检索
 */
public class AuthMdcFilter extends OncePerRequestFilter {
    /**
     * 在jwt认证完成后读取当前用户id，并在本次请求日志上下文中记录
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try(TraceContext.MdcScope ignored = putAuthenticatedUser(authentication)){
            filterChain.doFilter(request, response);
        }
    }

    private TraceContext.MdcScope putAuthenticatedUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof  Jwt jwt)) {
            return TraceContext.putUserId(null);
        }
        return TraceContext.putUserId(Long.valueOf(jwt.getSubject()));
    }
}

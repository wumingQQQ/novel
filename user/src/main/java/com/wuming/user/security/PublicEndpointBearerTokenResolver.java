package com.wuming.user.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

import java.util.Set;

/**
 * 在匿名认证入口忽略客户端遗留的 Bearer Token，避免过期 Token 抢先导致 401。
 */
public final class PublicEndpointBearerTokenResolver implements BearerTokenResolver {

    private static final Set<String> PUBLIC_PATHS = Set.of("/auth/login", "/auth/register");

    private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        String path = contextPath == null || contextPath.isEmpty()
                ? requestUri
                : requestUri.substring(contextPath.length());

        return PUBLIC_PATHS.contains(path) ? null : delegate.resolve(request);
    }
}

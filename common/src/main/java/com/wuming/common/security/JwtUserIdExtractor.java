package com.wuming.common.security;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public class JwtUserIdExtractor {
    /**
     * 从当前认证上下文中读取登录用户id
     */
    public Long requireUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未登录");
        }
        return Long.valueOf(jwt.getSubject());
    }
}

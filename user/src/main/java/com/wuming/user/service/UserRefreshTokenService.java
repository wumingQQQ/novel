package com.wuming.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.common.security.JwtProperties;
import com.wuming.user.domain.entity.UserRefreshToken;
import com.wuming.user.infrastructure.mapper.UserRefreshTokenMapper;
import com.wuming.user.security.RefreshTokenIssue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserRefreshTokenService {
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRefreshTokenMapper refreshTokenMapper;
    private final JwtProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 为用户签发刷新令牌。明文只返回给客户端，服务端仅保存哈希。
     */
    public RefreshTokenIssue issue(Long userId) {
        String token = generateToken();
        LocalDateTime expiresTime = LocalDateTime.now().plusDays(properties.getRefreshExpiresDays());

        UserRefreshToken refreshToken = new UserRefreshToken();
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(hash(token));
        refreshToken.setExpiresTime(expiresTime);
        refreshTokenMapper.insert(refreshToken);

        return new RefreshTokenIssue(token, expiresTime);
    }

    /**
     * 消费刷新令牌并吊销旧令牌，调用方随后签发新的刷新令牌。
     */
    public Long consume(String token) {
        UserRefreshToken refreshToken = getValidRefreshToken(token);
        LocalDateTime now = LocalDateTime.now();
        refreshToken.setLastUsedTime(now);
        refreshToken.setRevokedTime(now);
        refreshTokenMapper.updateById(refreshToken);
        return refreshToken.getUserId();
    }

    /**
     * 吊销刷新令牌。无效令牌按未登录处理，避免客户端误判退出成功。
     */
    public void revoke(String token) {
        UserRefreshToken refreshToken = getValidRefreshToken(token);
        refreshToken.setRevokedTime(LocalDateTime.now());
        refreshTokenMapper.updateById(refreshToken);
    }

    private UserRefreshToken getValidRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌无效");
        }
        UserRefreshToken refreshToken = refreshTokenMapper.selectOne(new LambdaQueryWrapper<UserRefreshToken>()
                .eq(UserRefreshToken::getTokenHash, hash(token.trim())));
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌无效");
        }
        if (refreshToken.getRevokedTime() != null) {
            revokeAllActive(refreshToken.getUserId());
            log.warn("检测到刷新令牌重放，已吊销用户全部有效刷新令牌，userId: {}, tokenId: {}",
                    refreshToken.getUserId(), refreshToken.getId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌已失效，请重新登录");
        }
        if (refreshToken.getExpiresTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌已过期");
        }
        return refreshToken;
    }

    private void revokeAllActive(Long userId) {
        refreshTokenMapper.update(null, new LambdaUpdateWrapper<UserRefreshToken>()
                .eq(UserRefreshToken::getUserId, userId)
                .isNull(UserRefreshToken::getRevokedTime)
                .set(UserRefreshToken::getRevokedTime, LocalDateTime.now()));
    }

    private String generateToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }
}

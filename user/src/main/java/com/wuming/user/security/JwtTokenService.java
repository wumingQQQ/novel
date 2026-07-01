package com.wuming.user.security;

import com.wuming.user.config.AuthJwtProperties;
import com.wuming.user.domain.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * jwt令牌服务，负责为已认证用户签发访问令牌
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {
    private final JwtEncoder jwtEncoder;
    private final AuthJwtProperties properties;

    /**
     * 为用户签发访问令牌
     */
    public String createAccessToken(User user){
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.getExpiresMinutes() * 60))
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .build();

        JwsHeader header = JwsHeader.with(() -> "HS256").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}

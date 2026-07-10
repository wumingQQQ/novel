package com.wuming.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Arrays;
import java.util.Set;

@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.security.oauth2.jwt.JwtDecoder")
@EnableConfigurationProperties({JwtProperties.class})
public class JwtResourceServerAutoConfiguration {

    private static final Set<String> LOCAL_AUTH_DISABLED_PROFILES = Set.of("dev", "test", "local");

    /**
     * 创建jwt解析器，供各模块校验Bearer Token
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "auth.jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.requireHs256SecretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(properties.requireIssuer());
        decoder.setJwtValidator(issuerValidator);
        return decoder;
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtUserIdExtractor currentUser() {
        return new JwtUserIdExtractor();
    }

    @Bean
    @ConditionalOnProperty(prefix = "auth.jwt", name = "enabled", havingValue = "false")
    public AuthJwtDisabledGuard authJwtDisabledGuard(Environment environment) {
        return new AuthJwtDisabledGuard(environment);
    }

    public static class AuthJwtDisabledGuard {
        public AuthJwtDisabledGuard(Environment environment) {
            boolean localProfile = Arrays.stream(environment.getActiveProfiles())
                    .anyMatch(LOCAL_AUTH_DISABLED_PROFILES::contains);
            if (!localProfile) {
                throw new IllegalStateException("JWT authentication can only be disabled in dev/test/local profiles");
            }
        }
    }
}

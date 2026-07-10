package com.wuming.user.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.wuming.common.security.JwtProperties;
import com.wuming.user.security.AuthMdcFilter;
import com.wuming.user.security.PublicEndpointBearerTokenResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 用户模块安全配置，负责声明接口访问规则和密码哈希策略
 */
@Configuration
public class SecurityConfig {

    /**
     * 配置用户模块HTTP安全规则
     */
    @Bean
    @ConditionalOnProperty(prefix = "auth.jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/users", "/auth/login", "/auth/register")
                        .permitAll()
                        .anyRequest()
                        .authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(new PublicEndpointBearerTokenResolver())
                        .jwt(Customizer.withDefaults())
                )
                .addFilterAfter(new AuthMdcFilter(), BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Profile({"dev", "test", "local"})
    @ConditionalOnProperty(prefix = "auth.jwt", name = "enabled", havingValue = "false")
    public SecurityFilterChain permitAllSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(registry -> registry
                        .anyRequest()
                        .permitAll()
                )
                .build();
    }

    /**
     * 密码哈希器
     * @return BCrypt
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 基于本地秘钥创建jwt签发器
     */
    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(properties.requireHs256SecretKey()));
    }

}

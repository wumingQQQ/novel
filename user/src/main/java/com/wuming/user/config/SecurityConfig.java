package com.wuming.user.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 用户模块安全配置，负责声明接口访问规则和密码哈希策略
 */
@Configuration
@EnableConfigurationProperties(AuthJwtProperties.class)
public class SecurityConfig {

    /**
     * 配置用户模块HTTP安全规则
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers("/users", "/auth/login", "/auth/register")
                        .permitAll()
                        .anyRequest()
                        .authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
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
    public JwtEncoder jwtEncoder(AuthJwtProperties properties) {
        SecretKey secretKey = new SecretKeySpec(
                properties.getSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    /**
     * 基于本地秘钥创建jwt解析器，用于校验Bearer Token
     */
    @Bean
    public JwtDecoder jwtDecoder(AuthJwtProperties properties) {
        SecretKey secretKey = new SecretKeySpec(
                properties.getSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

}

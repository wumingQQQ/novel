package com.wuming.common.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuming.common.redis.core.RedisHashOps;
import com.wuming.common.redis.core.RedisJsonOps;
import com.wuming.common.redis.core.RedisListOps;
import com.wuming.common.redis.core.RedisStringOps;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@RequiredArgsConstructor
public class CommonRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisJsonOps redisJsonOps(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        return new RedisJsonOps(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisHashOps redisHashOps(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        return new RedisHashOps(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisStringOps redisStringOps(
            StringRedisTemplate redisTemplate
    ) {
        return new RedisStringOps(redisTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisListOps redisListOps(
            StringRedisTemplate redisTemplate
    ) {
        return new RedisListOps(redisTemplate);
    }
}

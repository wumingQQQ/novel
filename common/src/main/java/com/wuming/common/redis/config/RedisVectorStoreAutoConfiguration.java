package com.wuming.common.redis.config;

import com.wuming.common.redis.vector.RedisVectorStoreFactory;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(RedisVectorStore.class)
public class RedisVectorStoreAutoConfiguration {

    /**
     * 创建向量存储工厂。只有业务模块显式引入 Spring AI Redis Store 时才会生效。
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisVectorStoreFactory redisVectorStoreFactory() {
        return new RedisVectorStoreFactory();
    }
}

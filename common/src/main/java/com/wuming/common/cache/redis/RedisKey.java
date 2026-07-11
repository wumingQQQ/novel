package com.wuming.common.cache.redis;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 用于拼接key的工具类
 */
public final class RedisKey {
    private RedisKey() {
    }

    public static String join(String... parts){
        return Arrays.stream(parts)
                .filter(part -> part!= null && !part.isBlank())
                .collect(Collectors.joining(":"));
    }
}

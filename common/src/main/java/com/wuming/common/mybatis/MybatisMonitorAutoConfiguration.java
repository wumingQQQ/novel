package com.wuming.common.mybatis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class MybatisMonitorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SqlSummaryInterceptor sqlSummaryInterceptor() {
        return new SqlSummaryInterceptor();
    }
}

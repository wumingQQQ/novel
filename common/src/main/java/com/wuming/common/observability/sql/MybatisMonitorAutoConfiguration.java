package com.wuming.common.observability.sql;

import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureBefore(name = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnClass(Interceptor.class)
public class MybatisMonitorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SqlSummaryInterceptor sqlSummaryInterceptor() {
        return new SqlSummaryInterceptor();
    }
}

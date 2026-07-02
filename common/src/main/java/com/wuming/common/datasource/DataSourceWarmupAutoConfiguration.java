package com.wuming.common.datasource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 数据源预热自动配置。
 * 应用启动后主动获取一次数据库连接，避免首个真实请求承担连接池初始化成本。
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(DataSource.class)
public class DataSourceWarmupAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(DataSourceWarmupRunner.class)
    public DataSourceWarmupRunner dataSourceWarmupRunner(DataSource dataSource) {
        return new DataSourceWarmupRunner(dataSource);
    }

    /**
     * 启动后执行轻量级 SQL，提前完成数据库连接池初始化。
     */
    public static class DataSourceWarmupRunner implements ApplicationRunner {

        private final DataSource dataSource;

        public DataSourceWarmupRunner(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void run(ApplicationArguments args) {
            long start = System.currentTimeMillis();
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                log.info("数据库连接预热完成，costMs: {}", System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("数据库连接预热失败，costMs: {}", System.currentTimeMillis() - start, e);
            }
        }
    }
}

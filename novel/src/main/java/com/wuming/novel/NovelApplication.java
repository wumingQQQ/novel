package com.wuming.novel;

import com.wuming.novel.config.TaskPageProperties;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableDubbo
@EnableScheduling
@SpringBootApplication
@MapperScan(basePackages = "com.wuming.novel.infrastructure.mapper")
@EnableConfigurationProperties(TaskPageProperties.class)
public class NovelApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovelApplication.class, args);
    }

}

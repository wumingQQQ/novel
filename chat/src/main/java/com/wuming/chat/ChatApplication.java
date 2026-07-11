package com.wuming.chat;

import com.wuming.chat.config.ChatMemoryProperties;
import com.wuming.chat.config.ChatSseProperties;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableDubbo
@MapperScan("com.wuming.chat.infrastructure.mapper")
@SpringBootApplication
@EnableConfigurationProperties({ChatMemoryProperties.class, ChatSseProperties.class})
public class ChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatApplication.class, args);
    }

}

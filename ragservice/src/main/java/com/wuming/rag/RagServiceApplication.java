package com.wuming.rag;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableDubbo
@SpringBootApplication
public class RagServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagServiceApplication.class, args);
    }
}

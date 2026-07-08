package com.wuming.chat.config.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {
    private Double temperature = 0.6;
    private LlmProvider deepseek = new LlmProvider();
}

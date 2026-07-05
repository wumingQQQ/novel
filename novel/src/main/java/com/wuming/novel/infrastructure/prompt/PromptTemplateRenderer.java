package com.wuming.novel.infrastructure.prompt;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PromptTemplateRenderer {
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public String render(String templatePath, Map<String, ?> variables) {
        String template = templateCache.computeIfAbsent(templatePath, this::loadTemplate);
        String rendered = template;
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            String value = entry.getValue() == null ? "" : entry.getValue().toString();
            rendered = rendered.replace(placeholder, value);
        }
        return rendered;
    }

    private String loadTemplate(String templatePath) {
        try {
            ClassPathResource resource = new ClassPathResource(templatePath);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("提示词模板加载失败: " + templatePath, e);
        }
    }
}

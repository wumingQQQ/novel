package com.wuming.novel.infrastructure.prompt;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PromptTemplateRenderer {
    private final ResourceLoader resourceLoader;
    private final Map<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();

    public PromptTemplateRenderer() {
        this(new DefaultResourceLoader());
    }

    public PromptTemplateRenderer(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String render(String templatePath, Map<String, ?> variables) {
        PromptTemplate promptTemplate = templateCache.computeIfAbsent(templatePath, this::loadTemplate);
        return promptTemplate.render(templateVariables(variables));
    }

    private PromptTemplate loadTemplate(String templatePath) {
        Resource resource = resourceLoader.getResource(location(templatePath));
        return new PromptTemplate(resource);
    }

    private String location(String templatePath) {
        if (templatePath.startsWith("classpath:")) {
            return templatePath;
        }
        return "classpath:" + templatePath;
    }

    private Map<String, Object> templateVariables(Map<String, ?> variables) {
        Map<String, Object> result = new LinkedHashMap<>();
        variables.forEach((key, value) -> result.put(key, value == null ? "" : value));
        return result;
    }
}

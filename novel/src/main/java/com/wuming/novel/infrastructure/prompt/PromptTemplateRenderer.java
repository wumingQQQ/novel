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

    /**
     * 渲染单一提示词模板（向后兼容）
     */
    public String render(String templatePath, Map<String, ?> variables) {
        PromptTemplate promptTemplate = templateCache.computeIfAbsent(templatePath, this::loadTemplate);
        return promptTemplate.render(templateVariables(variables));
    }

    /**
     * 渲染系统提示词和用户提示词（新方法）
     * @param systemTemplatePath 系统提示词模板路径，如 "prompts/system/role-profile-build.st"
     * @param userTemplatePath 用户提示词模板路径，如 "prompts/user/role-profile-build.st"
     * @param variables 变量映射
     * @return 包含系统提示词和用户提示词的结果对象
     */
    public DualPrompt renderDual(String systemTemplatePath, String userTemplatePath, Map<String, ?> variables) {
        String systemPrompt = render(systemTemplatePath, variables);
        String userPrompt = render(userTemplatePath, variables);
        return new DualPrompt(systemPrompt, userPrompt);
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

    /**
     * 双提示词结果
     */
    public record DualPrompt(String systemPrompt, String userPrompt) {}
}

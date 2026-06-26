package com.wuming.chat.config.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

@Getter
@Setter
public class LlmProvider {
    private String baseUrl;
    private String model;
    private String apiKey;

    /**
     * 创建普通文本聊天模型；角色聊天不强制 JSON 响应格式。
     */
    public ChatModel chatModel(Double temperature) {
        if (baseUrl == null || model == null || apiKey == null) {
            throw new IllegalStateException("baseUrl or model or apiKey 为空，模型无法正确配置");
        }
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .build())
                .build();
    }
}

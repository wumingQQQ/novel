package com.wuming.novel.config.llm;

import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;

@Setter
@Getter
public class LlmProvider {
    private String baseUrl;
    private String model;
    private String apiKey;

    public ChatModel chatModel(Double temperature) {
        if (baseUrl == null || model == null || apiKey == null) {
            throw new IllegalStateException("DeepSeek配置不完整，请检查 llm.deepseek.base-url、api-key、model");
        }
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .build())
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                        .build())
                .build();
    }
}

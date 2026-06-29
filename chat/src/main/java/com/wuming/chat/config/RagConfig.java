package com.wuming.chat.config;

import com.wuming.chat.config.llm.RagProperties;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;

import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {
    @Bean
    public EmbeddingModel embeddingModel(RagProperties ragProperties) {
        RagProperties.Embedding embedding = ragProperties.getEmbedding();

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(embedding.getBaseUrl())
                .apiKey(embedding.getApiKey())
                .build();
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embedding.getModel())
                .dimensions(embedding.getDimensions())
                .build();

        return new OpenAiEmbeddingModel(api, MetadataMode.NONE, options);
    }
}

package com.wuming.novel.llm.checker;

import com.wuming.novel.domain.llmresponse.LayerSplitResponse;
import com.wuming.novel.domain.llmresponse.LayerSplitResponseWrapper;
import com.wuming.novel.exception.LLMResponseEmptyException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LayerSplitResponseChecker {
    public List<LayerSplitResponse> check(Long novelId, int chapterCount, int maxLayers, LayerSplitResponseWrapper responseWrapper) {
        if (responseWrapper == null || responseWrapper.layers() == null || responseWrapper.layers().isEmpty()) {
            throw new LLMResponseEmptyException("小说" + novelId + "分层时llm响应为空，请稍后重试");
        }

        List<LayerSplitResponse> responses = responseWrapper.layers();
        if (responses.size() > maxLayers) {
            throw new LlmResponseCheckException("小说" + novelId
                    + "分层数量超过限制，actual: " + responses.size()
                    + ", max: " + maxLayers);
        }

        int expectedStartChapter = 1;
        for (int i = 0; i < responses.size(); i++) {
            LayerSplitResponse response = responses.get(i);
            checkLayerResponse(novelId, chapterCount, response, i, expectedStartChapter);
            expectedStartChapter = response.endChapter() + 1;
        }

        if (expectedStartChapter != chapterCount + 1) {
            throw new LlmResponseCheckException("小说" + novelId
                    + "分层未完整覆盖章节，expectedEndChapter: " + chapterCount
                    + ", actualEndChapter: " + (expectedStartChapter - 1));
        }
        return responses;
    }

    private void checkLayerResponse(Long novelId, int chapterCount, LayerSplitResponse response, int index, int expectedStartChapter) {
        if (response == null) {
            throw new LlmResponseCheckException("小说" + novelId + "分层结果为空，layerIndex: " + (index + 1));
        }
        if (response.layerIndex() != index + 1) {
            throw new LlmResponseCheckException("小说" + novelId
                    + "分层序号不连续，expectedLayerIndex: " + (index + 1)
                    + ", actualLayerIndex: " + response.layerIndex());
        }
        if (response.layerName() == null || response.layerName().isBlank()) {
            throw new LlmResponseCheckException("小说" + novelId + "分层名称为空，layerIndex: " + response.layerIndex());
        }
        if (response.startChapter() != expectedStartChapter) {
            throw new LlmResponseCheckException("小说" + novelId
                    + "分层起始章节不连续，layerIndex: " + response.layerIndex()
                    + ", expectedStartChapter: " + expectedStartChapter
                    + ", actualStartChapter: " + response.startChapter());
        }
        if (response.endChapter() < response.startChapter()) {
            throw new LlmResponseCheckException("小说" + novelId
                    + "分层结束章节小于起始章节，layerIndex: " + response.layerIndex()
                    + ", startChapter: " + response.startChapter()
                    + ", endChapter: " + response.endChapter());
        }
        if (response.startChapter() < 1 || response.endChapter() > chapterCount) {
            throw new LlmResponseCheckException("小说" + novelId
                    + "分层章节越界，layerIndex: " + response.layerIndex()
                    + ", chapterCount: " + chapterCount
                    + ", startChapter: " + response.startChapter()
                    + ", endChapter: " + response.endChapter());
        }
    }
}

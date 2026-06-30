package com.wuming.chat.rag.index;

import com.wuming.api.scene.dto.SceneDto;
import com.wuming.chat.integration.message.dto.ChapterSceneSplitCompleteMessage;
import com.wuming.chat.infrastructure.observability.TraceContext;
import com.wuming.chat.rag.redis.SceneVectorStoreService;
import com.wuming.chat.integration.rpc.scene.SceneContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SceneRagIndexService {
    private final SceneContextService sceneContextService;
    private final SceneVectorStoreService vectorStoreService;

    /**
     * 查询指定章节的场景并写入向量库，用于后续聊天RAG召回。
     *
     * @param message 章节切分完成事件消息
     */
    public void indexChapterScenes(ChapterSceneSplitCompleteMessage message) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(message.getJobId())) {
            long start = System.currentTimeMillis();
            log.info("开始索引章节场景，chapterId: {}", message.getChapterId());

            List<SceneDto> scenes = sceneContextService.listScenesByChapter(
                    message.getJobId(),
                    message.getChapterId()
            );

            int success = 0;
            int failed = 0;

            // TODO 后续考虑做失败重试
            for (SceneDto scene : scenes) {
                try {
                    vectorStoreService.upsertScene(message.getJobId(), scene);
                    success++;
                } catch (Exception e) {
                    failed++;
                    log.warn("场景向量索引失败，chapterId: {}, sceneId: {}",
                            message.getChapterId(), scene.getSceneId(), e);
                }
            }

            log.info("章节场景向量索引完成，chapterId: {}, total: {}, success: {}, "
                            + "failed: {}, costMs: {}",
                    message.getChapterId(), scenes.size(), success, failed,
                    System.currentTimeMillis() - start);
        }
    }
}


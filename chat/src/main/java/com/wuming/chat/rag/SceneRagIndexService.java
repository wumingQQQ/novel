package com.wuming.chat.rag;

import com.wuming.api.scene.dto.SceneDto;
import com.wuming.chat.message.eventdto.ChapterSceneSplitCompleteMessage;
import com.wuming.chat.rag.redis.SceneVectorStoreService;
import com.wuming.chat.rpc.scene.SceneContextService;
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

    public void indexChapterScenes(ChapterSceneSplitCompleteMessage message){
        List<SceneDto> scenes = sceneContextService.listScenesByChapter(
                message.getJobId(),
                message.getChapterId()
        );

        int success = 0;
        int failed = 0;

        // TODO 后续考虑做失败重试
        for(SceneDto scene : scenes){
            try{
                vectorStoreService.upsertScene(message.getJobId(), scene);
                success++;
            }
            catch (Exception e){
                failed++;
                log.warn(
                        "场景向量索引失败，jobId: {}, chapterId: {}, sceneId: {}",
                        message.getJobId(),
                        message.getChapterId(),
                        scene.getSceneId(),
                        e
                );
            }
        }

        log.info(
                "章节场景向量索引完成，jobId: {}, chapterId: {}, total: {}, success: {}, failed: {}",
                message.getJobId(),
                message.getChapterId(),
                scenes.size(),
                success,
                failed
        );
    }
}

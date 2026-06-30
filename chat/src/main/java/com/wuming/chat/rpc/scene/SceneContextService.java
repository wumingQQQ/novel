package com.wuming.chat.rpc.scene;

import com.wuming.api.scene.SceneFacade;
import com.wuming.api.scene.dto.SceneDto;
import com.wuming.chat.observability.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SceneContextService {
    @DubboReference(url = "tri://127.0.0.1:50051", timeout = 15000)
    private SceneFacade sceneFacade;

    /**
     * 通过远程接口获取某章节的场景。
     *
     * @param jobId 任务id
     * @param chapterId 章节id
     * @return 章节场景列表
     */
    public List<SceneDto> listScenesByChapter(Long jobId, Long chapterId) {
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(jobId)) {
            long start = System.currentTimeMillis();
            log.debug("开始远程查询章节场景，chapterId: {}", chapterId);
            List<SceneDto> scenes = sceneFacade.listScenesByChapter(jobId, chapterId);
            log.debug("远程章节场景查询完成，chapterId: {}, sceneCount: {}, costMs: {}",
                    chapterId, scenes.size(), System.currentTimeMillis() - start);
            return scenes;
        }
    }
}

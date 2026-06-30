package com.wuming.chat.rpc.scene;

import com.wuming.api.scene.SceneFacade;
import com.wuming.api.scene.dto.SceneDto;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SceneContextService {
    @DubboReference(url = "tri://127.0.0.1:50051", timeout = 15000)
    private SceneFacade sceneFacade;

    /**
     * 通过远程接口获取某章节的场景
     */
    public List<SceneDto> listScenesByChapter(Long jobId, Long chapterId){
        return sceneFacade.listScenesByChapter(jobId, chapterId);
    }
}

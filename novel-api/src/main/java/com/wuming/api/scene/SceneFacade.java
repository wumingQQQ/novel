package com.wuming.api.scene;

import com.wuming.api.scene.dto.SceneDto;

import java.util.List;

public interface SceneFacade {
    /**
     * 查询指定任务中某一章节切分出的场景列表
     *
     * @param jobId 画像构建任务id
     * @param chapterId 章节id
     * @return 按场景序号升序排列的场景列表
     */
    List<SceneDto> listScenesByChapter(Long jobId, Long chapterId);
}

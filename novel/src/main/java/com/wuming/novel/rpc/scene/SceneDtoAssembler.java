package com.wuming.novel.rpc.scene;

import com.wuming.api.scene.dto.SceneDto;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Scene;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SceneDtoAssembler {

    public SceneDto toSceneDto(Scene scene, Chapter chapter) {
        SceneDto dto = new SceneDto();
        dto.setSceneId(scene.getId());
        dto.setNovelId(chapter.getNovelId());
        dto.setChapterId(scene.getChapterId());
        dto.setSceneSequence(scene.getSequence());
        dto.setChapterSequence(chapter.getSequence());
        dto.setContent(scene.getContent());

        return dto;
    }
}

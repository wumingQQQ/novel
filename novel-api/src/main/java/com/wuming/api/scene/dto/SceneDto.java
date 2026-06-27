package com.wuming.api.scene.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SceneDto implements Serializable {
    private Long sceneId;
    private Long chapterId;
    private Long novelId;
    private Integer chapterSequence;
    private Integer sceneSequence;
    private String content;

    @Serial
    private static final long serialVersionUID = 1L;
}

package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Passage 与出场角色的映射。
 */
@Data
@TableName("passage_characters")
public class PassageCharacter implements Serializable {

    private Long passageId;

    private String characterName;

    @Serial
    private static final long serialVersionUID = 1L;
}

package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 小说检索文本块。
 *
 * Passage 是向量索引、候选材料筛选和角色样本抽取的基础文本单元。
 */
@Data
@TableName(value = "novel_passages")
public class NovelPassage implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long novelId;

    private Long chapterId;

    /**
     * 原文内容
     */
    private String content;

    /**
     * 文本块在整本小说中的顺序
     */
    private Integer sequence;

    /**
     * 文本块在当前章节内的顺序
     */
    private Integer innerSequence;

    /**
     * 对应章节内起始段落编号
     */
    private Integer startParagraph;

    /**
     * 对应章节内结束段落编号
     */
    private Integer endParagraph;

    /**
     * 向量索引状态：PENDING、INDEXED、FAILED
     */
    private String vectorStatus;

    /**
     * 向量索引失败原因
     */
    private String vectorError;

    /**
     * 向量索引完成时间
     */
    private LocalDateTime indexedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

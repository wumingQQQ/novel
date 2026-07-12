package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wuming.novel.domain.enums.NovelPreprocessStage;
import com.wuming.novel.domain.enums.NovelPreprocessStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 一部小说共享章节、分析和 Passage 产物的预处理状态。 */
@Data
@TableName("novel_preprocesses")
public class NovelPreprocess {
    @TableId
    private Long id;
    private Long novelId;
    private NovelPreprocessStatus status;
    private NovelPreprocessStage currentStage;
    private NovelPreprocessStage completedStage;
    private Integer chapterCount;
    private Integer passageCount;
    private String failureReason;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

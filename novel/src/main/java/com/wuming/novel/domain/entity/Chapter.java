package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "chapters", autoResultMap = true)
public class Chapter implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private String title;

    /**
     * 章节在小说中的顺序。
     */
    private int sequence;
    private String content;

    /**
     * 历史章节摘要字段，当前构建链路不再写入。
     */
    private String summary;

    /**
     * 历史场景切换段落号字段，当前Passage切分固定使用滑动窗口。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> sceneBoundaries;

    /**
     * 历史章节分析状态字段，当前构建链路不再写入。
     */
    private String analysisStatus;

    /**
     * 历史章节分析失败原因字段，当前构建链路不再写入。
     */
    private String analysisError;

    /**
     * 历史章节分析完成时间字段，当前构建链路不再写入。
     */
    private LocalDateTime analyzedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

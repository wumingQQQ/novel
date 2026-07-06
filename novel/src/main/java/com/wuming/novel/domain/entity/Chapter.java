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
     * 章节摘要，供召回后补充 prompt 上下文。
     */
    private String summary;

    /**
     * 场景切换段落号列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> sceneBoundaries;

    /**
     * 章节分析状态：PENDING、DONE、FAILED。
     */
    private String analysisStatus;

    /**
     * 章节分析失败原因。
     */
    private String analysisError;

    /**
     * 章节分析完成时间。
     */
    private LocalDateTime analyzedTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

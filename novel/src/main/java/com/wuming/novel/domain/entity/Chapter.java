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

@Data
@TableName("chapters")
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
     * 章节主要出场人物，初版使用逗号分隔。
     */
    private String mainCharacters;

    /**
     * 场景切换段落号列表，按 JSON 数组保存。
     */
    private String sceneBoundaries;

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

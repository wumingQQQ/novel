package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("scenes")
public class Scene implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer novelId;
    private Integer chapterId;
    private int sequence;
    private String content;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

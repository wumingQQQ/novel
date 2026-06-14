package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("layers")
public class Layer {
    @TableId(type = IdType.AUTO)
    private int id;

    private int layerId;

    private int novelId;
    private int startChapterId;
    private int endChapterId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

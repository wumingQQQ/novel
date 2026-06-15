package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("layers")
public class Layer implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private int layerId;

    private Integer novelId;
    private Integer startChapterId;
    private Integer endChapterId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

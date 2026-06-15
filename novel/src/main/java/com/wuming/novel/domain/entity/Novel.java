package com.wuming.novel.domain.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("novels")
public class Novel implements Serializable {
    @TableId(type = IdType.AUTO)
    private int id;
    private String title;
    private String filePath;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

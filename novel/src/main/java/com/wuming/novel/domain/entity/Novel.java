package com.wuming.novel.domain.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("novels")
public class Novel {
    @TableId(type = IdType.AUTO)
    private int id;
    private String title;
    private String filePath;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

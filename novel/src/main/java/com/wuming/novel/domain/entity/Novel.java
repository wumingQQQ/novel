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
@TableName("novels")
public class Novel implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String filePath;
    private Long uploaderId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

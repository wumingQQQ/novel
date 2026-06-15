package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("scenes")
public class Scene implements Serializable {
    @TableId
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

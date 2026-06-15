package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@TableName("chapters")
public class Chapter implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer novelId;
    private String title;
    // 小说中章节id
    private int sequence;
    private String content;

    @Serial
    private static final long serialVersionUID = 1L;
}

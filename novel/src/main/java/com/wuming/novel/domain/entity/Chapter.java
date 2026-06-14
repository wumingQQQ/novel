package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("chapters")
public class Chapter {
    @TableId(type = IdType.AUTO)
    private int id;
    private int novelId;
    private String title;
    // 小说中章节id
    private int sequence;
    private String content;
}

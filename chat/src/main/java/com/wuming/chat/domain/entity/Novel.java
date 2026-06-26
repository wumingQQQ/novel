package com.wuming.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("novels")
public class Novel {
    @TableId
    private Long id;
    private String name;
}

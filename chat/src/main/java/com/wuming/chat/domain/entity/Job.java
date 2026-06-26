package com.wuming.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("jobs")
public class Job {
    @TableId
    private Long id;
    private Long novelId;
    private String protagonistName;
    private String targetName;
    private Integer stage;
}

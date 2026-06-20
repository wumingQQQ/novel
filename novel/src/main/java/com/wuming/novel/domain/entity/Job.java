package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.wuming.novel.domain.enums.JobStage;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("jobs")
public class Job implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long novelId;
    private String protagonistName;      // 用户将会扮演的角色
    private String targetName;      // llm将会扮演的角色
    private JobStage stage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

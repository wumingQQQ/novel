package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "interaction_profiles", autoResultMap = true)
public class InteractionProfile implements Serializable {
    @TableId(type = IdType.AUTO)
    @JsonIgnore
    private Integer id;

    @JsonIgnore
    private Integer characterId;    // 关于谁与主角的交流画像
    private String protagonistName;     // 主角
    private String tone;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> keyEvents;     // 二者经历的关键事件
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> conservationSamples;       // 对话示例

    @TableField(fill = FieldFill.INSERT)
    @JsonIgnore
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonIgnore
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

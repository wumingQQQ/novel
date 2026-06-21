package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName(value = "interaction_profiles", autoResultMap = true)
public class InteractionProfile implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;

    private Long characterId;    // 关于谁与主角的交流画像
    private String protagonistName;     // 主角
    private String tone;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> keyEvents = new ArrayList<>();     // 二者经历的关键事件
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> conversationSamples = new ArrayList<>();       // 对话示例

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

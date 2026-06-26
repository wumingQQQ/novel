package com.wuming.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@TableName(value = "interaction_profiles", autoResultMap = true)
public class InteractionProfile {
    @TableId
    private Long id;
    private Long jobId;
    private Long characterId;
    private String protagonistName;
    private String tone;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> keyEvents = new ArrayList<>();
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> conversationSamples = new ArrayList<>();
}

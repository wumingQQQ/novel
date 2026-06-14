package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "profiles", autoResultMap = true)
public class CharacterProfile {
    @TableId(type = IdType.AUTO)
    private int id;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private BasicSetting basicSetting;
    // 后期考虑定义多个字段
    private String personality;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private SpeechStyle speechStyle;

    // 后期考虑增加成长弧线

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}


@Data
class BasicSetting{
    private String characterName;
    private int age;
    private String identity;    // 身份(学生等)
    private String presume;     // 角色设定，比如不会说谎等
}

@Data
class SpeechStyle{
    private String tone;    // 语气
    private String wordsHabit;      // 口癖
    private List<String> representativeLines;       // 代表语句
}

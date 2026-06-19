package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName(value = "character_profiles", autoResultMap = true)
public class CharacterProfile implements Serializable {
    @TableId(type = IdType.AUTO)
    @JsonIgnore
    private Integer id;

    @JsonIgnore
    private Integer jobId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private BasicSetting basicSetting = new BasicSetting();
    // 后期考虑定义多个字段
    private String personality;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private SpeechStyle speechStyle = new SpeechStyle();

    // 后期考虑增加成长弧线

    @TableField(fill = FieldFill.INSERT)
    @JsonIgnore
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonIgnore
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;

    @Data
    static
    public class BasicSetting implements Serializable {
        private String characterName;
        private int age;
        private String identity;    // 身份(学生等)
        private String presume;     // 角色设定，比如不会说谎等

        @Serial
        private static final long serialVersionUID = 1L;
    }

    @Data
    static
    public class SpeechStyle implements Serializable {
        private String tone;    // 语气
        private String wordsHabit;      // 口癖
        private List<String> representativeLines = new ArrayList<>();       // 代表语句

        @Serial
        private static final long serialVersionUID = 1L;
    }

}

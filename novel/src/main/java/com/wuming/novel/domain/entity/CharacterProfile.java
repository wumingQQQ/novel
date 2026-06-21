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
@TableName(value = "character_profiles", autoResultMap = true)
public class CharacterProfile implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private BasicSetting basicSetting = new BasicSetting();
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> coreTraits = new ArrayList<>();
    private String valueSystem;
    private String behaviorPatterns;
    private String emotionalPatterns;
    private String relationshipAttitude;
    private String weaknesses;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private SpeechStyle speechStyle = new SpeechStyle();

    // 后期考虑增加成长弧线

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
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

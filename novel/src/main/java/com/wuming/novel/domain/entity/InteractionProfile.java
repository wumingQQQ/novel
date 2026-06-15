package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "interaction_profiles", autoResultMap = true)
public class InteractionProfile implements Serializable {
    @TableId(type = IdType.AUTO)
    private int id;

    private int characterId;    // 关于谁与主角的交流画像
    private String protagonistName;     // 主角
    private String tone;
    private List<String> keyEvents;     // 二者经历的关键事件
    private List<String> conservationSamples;       // 对话示例

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

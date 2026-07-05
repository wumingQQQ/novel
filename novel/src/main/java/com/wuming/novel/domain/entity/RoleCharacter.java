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

/**
 * 小说角色唯一标识。
 *
 * 初版按“同一本小说 + 角色名称”唯一定位角色，chat 模块后续通过
 * characterId 关联会话和角色样本。
 */
@Data
@TableName(value = "role_characters", autoResultMap = true)
public class RoleCharacter implements Serializable {

    /**
     * 角色ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 小说ID
     */
    private Long novelId;

    /**
     * 小说名称，用于提示词背景和同名角色消歧
     */
    private String novelName;

    /**
     * 角色名称
     */
    private String characterName;

    /**
     * 角色别名，初版可为空
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> aliases = new ArrayList<>();

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @Serial
    private static final long serialVersionUID = 1L;
}

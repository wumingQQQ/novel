package com.wuming.novel.domain.entity.rel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.wuming.novel.domain.enums.PoolType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
@TableName(value = "scene_pool", autoResultMap = true)
public class ScenePool implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer sceneId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<PoolType, Double> pools;
}

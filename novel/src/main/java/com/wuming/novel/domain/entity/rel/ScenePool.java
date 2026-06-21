package com.wuming.novel.domain.entity.rel;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wuming.novel.domain.enums.PoolType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "scene_pool")
public class ScenePool implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sceneId;
    private Long novelId;
    private Long jobId;
    private PoolType poolType;
    private Double confidence;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

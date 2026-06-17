package com.wuming.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.wuming.novel.domain.enums.PoolType;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@TableName("evidences")
public class Evidence implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer novelId;
    private Integer layerId;
    private PoolType poolType;
    private String conclusion;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<Integer, String> supportQuotes;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Integer> sceneIndices;
    private Double confidence;
}

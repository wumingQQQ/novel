package com.wuming.novel.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum PoolType {
    SETTING("SETTING", "基础设定、家庭背景"),
    PERSONALITY("PERSONALITY", "性格、价值观"),
    SPEECH("SPEECH", "语气、说话方式"),
    INTERACTION("INTERACTION", "与男主的互动"),
    KEY_EVENT("KEY_EVENT", "关键事件");


    @EnumValue
    private final String code;
    @JsonValue
    private final String desc;
    PoolType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PoolType fromCode(String code) {
        for (PoolType poolType : PoolType.values()) {
            if (poolType.code.equals(code)) {
                return poolType;
            }
        }
        return null;
    }
}

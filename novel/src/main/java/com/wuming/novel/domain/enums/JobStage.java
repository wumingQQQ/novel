package com.wuming.novel.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum JobStage {
    PENDING(0, "队列中");


    @EnumValue
    private final int code;

    @JsonValue
    private final String desc;

    JobStage(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

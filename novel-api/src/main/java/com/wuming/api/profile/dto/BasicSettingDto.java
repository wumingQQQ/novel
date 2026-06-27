package com.wuming.api.profile.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class BasicSettingDto implements Serializable {
    private String characterName;
    private Integer age;
    private String identity;
    private String presume;

    @Serial
    private static final long serialVersionUID = 1L;
}

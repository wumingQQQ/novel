package com.wuming.api.role.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 个人角色版本归属与角色一致性的远程校验结果。
 */
@Data
public class RoleVersionValidationResultDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private boolean valid;
    private String code;
    private String message;

    /** 创建通过校验的结果。 */
    public static RoleVersionValidationResultDto success() {
        RoleVersionValidationResultDto result = new RoleVersionValidationResultDto();
        result.setValid(true);
        return result;
    }

    /** 创建未通过校验的结果。 */
    public static RoleVersionValidationResultDto failure(String code, String message) {
        RoleVersionValidationResultDto result = new RoleVersionValidationResultDto();
        result.setValid(false);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}

package com.wuming.api.profile.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class RoleContextResultDto implements Serializable {
    private boolean success;
    private String code;
    private String message;
    private RoleContextDto roleContext;

    @Serial
    private static final long serialVersionUID = 1L;

    public static RoleContextResultDto success(RoleContextDto roleContext) {
        RoleContextResultDto result = new RoleContextResultDto();
        result.setSuccess(true);
        result.setRoleContext(roleContext);
        return result;
    }

    public static RoleContextResultDto failure(String code, String message) {
        RoleContextResultDto result = new RoleContextResultDto();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
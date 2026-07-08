package com.wuming.api.role.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色运行时上下文查询结果。
 */
@Data
public class RoleRuntimeContextResultDto implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String code;
    private String message;
    private RoleRuntimeContextDto runtimeContext;

    public static RoleRuntimeContextResultDto success(RoleRuntimeContextDto runtimeContext) {
        RoleRuntimeContextResultDto result = new RoleRuntimeContextResultDto();
        result.setSuccess(true);
        result.setRuntimeContext(runtimeContext);
        return result;
    }

    public static RoleRuntimeContextResultDto failure(String code, String message) {
        RoleRuntimeContextResultDto result = new RoleRuntimeContextResultDto();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}

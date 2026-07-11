package com.wuming.novel.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建调整请求
 */
@Data
public class CreateRoleAdjustRequest {
    @NotNull
    private Long characterId;
    @NotBlank
    @Size(max = 200)
    private String requirement;
    @Size(max = 2000)
    private String chatText;
    private Long baseVersionId;
}

package com.wuming.novel.controller;

import com.wuming.common.security.JwtUserIdExtractor;
import com.wuming.common.web.ApiResponse;
import com.wuming.novel.domain.dto.PersonalRoleSummaryResponse;
import com.wuming.novel.domain.dto.PersonalRoleVersionResponse;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.service.adjust.PersonalRoleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户个人角色版本查询接口。
 */
@RestController
@RequestMapping("/personal-roles")
@RequiredArgsConstructor
public class PersonalRoleController {
    private final PersonalRoleQueryService personalRoleQueryService;
    private final JwtUserIdExtractor jwtUserIdExtractor;

    /**
     * 查询当前用户已创建个人版本的角色列表。
     *
     * @param authentication 当前认证上下文
     * @return 每个公共角色对应的最新个人版本摘要
     */
    @GetMapping
    public ApiResponse<List<PersonalRoleSummaryResponse>> listLatestRoles(Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            return ApiResponse.success(personalRoleQueryService.listLatestRoles(userId));
        }
    }

    /**
     * 查询当前用户在指定公共角色下的全部个人版本。
     *
     * @param characterId 公共角色主键
     * @param authentication 当前认证上下文
     * @return 按版本号倒序排列的个人角色版本列表
     */
    @GetMapping("/characters/{characterId}/versions")
    public ApiResponse<List<PersonalRoleVersionResponse>> listVersions(
            @PathVariable Long characterId,
            Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            return ApiResponse.success(personalRoleQueryService.listVersions(userId, characterId));
        }
    }

    /**
     * 查询当前用户在指定公共角色下的最新个人版本。
     *
     * @param characterId 公共角色主键
     * @param authentication 当前认证上下文
     * @return 最新个人角色版本；没有个人版本时data为null
     */
    @GetMapping("/characters/{characterId}/versions/latest")
    public ApiResponse<PersonalRoleVersionResponse> getLatestVersion(
            @PathVariable Long characterId,
            Authentication authentication) {
        Long userId = jwtUserIdExtractor.requireUserId(authentication);
        try (TraceContext.MdcScope ignoredUser = TraceContext.putUserId(userId)) {
            return ApiResponse.success(personalRoleQueryService.getLatestVersion(userId, characterId));
        }
    }
}

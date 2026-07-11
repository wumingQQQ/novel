package com.wuming.novel.controller;

import com.wuming.common.security.JwtUserIdExtractor;
import com.wuming.common.web.ApiResponse;
import com.wuming.novel.domain.dto.RoleWorkspaceDetailResponse;
import com.wuming.novel.domain.dto.RoleWorkspaceSummary;
import com.wuming.novel.service.workspace.RoleWorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户的角色与评测工作区接口。
 */
@RestController
@RequestMapping("/role-workspaces")
@RequiredArgsConstructor
public class RoleWorkspaceController {
    private final RoleWorkspaceService workspaceService;
    private final JwtUserIdExtractor jwtUserIdExtractor;

    /**
     * 查询当前用户参与过评测的公共角色工作区。
     *
     * @param authentication 当前认证信息
     * @return 按最近评测时间排序的角色工作区摘要
     */
    @GetMapping
    public ApiResponse<List<RoleWorkspaceSummary>> listWorkspaces(Authentication authentication) {
        return ApiResponse.success(workspaceService.listWorkspaces(jwtUserIdExtractor.requireUserId(authentication)));
    }

    /**
     * 查询当前用户在指定公共角色下的评测工作区详情。
     *
     * @param characterId 公共角色主键
     * @param authentication 当前认证信息
     * @return 当前用户专属评测概览
     */
    @GetMapping("/{characterId}")
    public ApiResponse<RoleWorkspaceDetailResponse> getWorkspace(
            @PathVariable Long characterId, Authentication authentication) {
        return ApiResponse.success(workspaceService.getWorkspace(
                jwtUserIdExtractor.requireUserId(authentication), characterId));
    }
}

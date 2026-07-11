package com.wuming.novel.controller;

import com.wuming.common.web.ApiResponse;
import com.wuming.novel.domain.dto.RolePublicPageResponse;
import com.wuming.novel.domain.dto.RolePublicPreview;
import com.wuming.novel.service.publicrole.RolePublicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向角色大厅的公共角色脱敏查询接口。
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RolePublicController {
    private final RolePublicService rolePublicService;

    /**
     * 分页查询已完成构建的公共角色摘要。
     *
     * @param keyword 角色名或小说名关键词
     * @param page 从1开始的页码
     * @param size 每页数量
     * @return 脱敏角色摘要分页结果
     */
    @GetMapping
    public ApiResponse<RolePublicPageResponse> listCharacters(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(rolePublicService.listCharacters(keyword, page, size));
    }

    /**
     * 查询单个公共角色的脱敏预览。
     *
     * @param characterId 公共角色主键
     * @return 脱敏角色预览
     */
    @GetMapping("/{characterId}")
    public ApiResponse<RolePublicPreview> getPreview(@PathVariable Long characterId) {
        return ApiResponse.success(rolePublicService.getPreview(characterId));
    }
}

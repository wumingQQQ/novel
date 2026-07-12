package com.wuming.novel.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.common.security.JwtUserIdExtractor;
import com.wuming.common.web.ApiResponse;
import com.wuming.common.web.PageResponse;
import com.wuming.novel.domain.dto.NovelDetailResponse;
import com.wuming.novel.domain.dto.NovelSummaryResponse;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.infrastructure.mapper.NovelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

/** 面向小说库的公开分页查询接口。 */
@RestController
@RequestMapping("/novels")
@RequiredArgsConstructor
public class NovelLibraryController {
    private final NovelMapper novelMapper;
    private final JwtUserIdExtractor jwtUserIdExtractor;

    @GetMapping
    public ApiResponse<PageResponse<NovelSummaryResponse>> list(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        int normalizedPage = Math.max(1, page); int normalizedSize = Math.min(48, Math.max(1, size));
        Long userId = "MINE".equalsIgnoreCase(scope) ? jwtUserIdExtractor.requireUserId(authentication) : null;
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Novel> result = novelMapper.selectPage(Page.of(normalizedPage, normalizedSize),
                new LambdaQueryWrapper<Novel>().eq(userId != null, Novel::getUploaderId, userId)
                        .and(normalizedKeyword != null, query -> query
                                .like(Novel::getName, normalizedKeyword)
                                .or()
                                .like(Novel::getOriginalFilename, normalizedKeyword))
                        .orderByDesc(Novel::getCreateTime).orderByDesc(Novel::getId));
        var items = result.getRecords().stream().map(novel -> new NovelSummaryResponse(novel.getId(), novel.getName(),
                novel.getOriginalFilename(), novel.getFileSize(), novel.getCreateTime(), userId != null)).toList();
        return ApiResponse.success(new PageResponse<>(items, result.getTotal(), normalizedPage, normalizedSize));
    }

    /**
     * 查询一部公开小说的详情；后续扩展字段只影响详情页，不改变列表契约。
     */
    @GetMapping("/{novelId}")
    public ApiResponse<NovelDetailResponse> getDetail(@PathVariable Long novelId) {
        Novel novel = novelMapper.selectById(novelId);
        if (novel == null) {
            throw new BusinessException(ErrorCode.NOVEL_NOT_FOUND, "您指定的小说不存在");
        }
        return ApiResponse.success(new NovelDetailResponse(novel.getId(), novel.getName(),
                novel.getOriginalFilename(), novel.getFileSize(), novel.getCreateTime()));
    }
}

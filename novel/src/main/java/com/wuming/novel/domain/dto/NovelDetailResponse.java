package com.wuming.novel.domain.dto;

import java.time.LocalDateTime;

/**
 * 小说详情页使用的公开响应。
 *
 * <p>列表摘要与详情响应分离，后续可在此增加作者、摘要和标签等字段。</p>
 */
public record NovelDetailResponse(
        Long id,
        String name,
        String originalFilename,
        Long fileSize,
        LocalDateTime createTime
) {
}

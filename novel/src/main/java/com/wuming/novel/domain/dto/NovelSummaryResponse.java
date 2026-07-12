package com.wuming.novel.domain.dto;

import java.time.LocalDateTime;

/** 小说库展示所需的公开摘要。 */
public record NovelSummaryResponse(Long id, String name, String originalFilename, Long fileSize,
                                   LocalDateTime createTime, boolean mine) {
}

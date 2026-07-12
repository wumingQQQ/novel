package com.wuming.novel.domain.dto;

/** 当前用户可查看的候选调整原文证据。 */
public record RoleAdjustEvidenceResponse(Long passageId, Long chapterId, Integer startParagraph,
                                         Integer endParagraph, String content) {
}

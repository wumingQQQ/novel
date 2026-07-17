package com.wuming.novel.service.support;

/**
 * 小说公共预处理阶段的共享进度快照。
 *
 * @param successCount 已成功处理的章节数
 * @param failureCount 等待接管重试的章节数
 * @param attemptCount 已消耗的共享自动尝试次数
 */
public record NovelPreprocessProgress(long successCount, long failureCount, long attemptCount) {
}

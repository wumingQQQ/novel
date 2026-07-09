package com.wuming.novel.integration.storage;

/**
 * 小说文件写入后的存储定位信息。
 */
public record StoredNovelFile(String storageType, String storagePath, long size) {
}

package com.wuming.novel.integration.storage;

import java.io.IOException;

/**
 * 小说原文文件存储接口。
 * <p>
 * 传入的bytes应当已经在业务入口统一转换为UTF-8。
 */
public interface NovelFileStorage {

    String storageType();

    StoredNovelFile store(byte[] bytes, Long userId) throws IOException;

    byte[] read(String storagePath) throws IOException;
}

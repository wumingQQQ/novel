package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.config.FileUploadProperties;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.exception.FileNotSupportException;
import com.wuming.novel.exception.FileTooLargeException;
import com.wuming.novel.infrastructure.mapper.NovelMapper;
import com.wuming.novel.integration.storage.NovelFileStorageRouter;
import com.wuming.novel.integration.storage.StoredNovelFile;
import com.wuming.novel.integration.rpc.user.UserContextService;
import com.wuming.novel.service.INovelService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class NovelService extends ServiceImpl<NovelMapper, Novel> implements INovelService {

    private final FileUploadProperties fileUploadProperties;
    private final UserContextService userContextService;
    private final NovelFileStorageRouter novelFileStorageRouter;


    /**
     * 上传小说
     * @return 小说id
     */
    @Override
    public Long saveNovel(MultipartFile file, Long userId) throws IOException {
        if(file.isEmpty()) {
            throw new FileNotSupportException("文件不能为空");
        }
        // 内部存在校验
        userContextService.requireUser(userId);

        if(file.getSize() > fileUploadProperties.getMaxFileSize().toBytes()){
            throw new FileTooLargeException("文件过大，请确保小于10MB");
        }
        String originalFilename = file.getOriginalFilename();
        String safeOriginalName = FilenameUtils.getName(originalFilename);
        String ext = FilenameUtils.getExtension(safeOriginalName);
        if(!"txt".equals(ext)){
            throw new FileNotSupportException("文件格式不支持，请上传txt格式的文件");
        }

        String baseName = FilenameUtils.getBaseName(safeOriginalName);
        byte[] utf8Bytes = normalizeToUtf8(file.getBytes());
        StoredNovelFile storedFile = novelFileStorageRouter.store(utf8Bytes, userId);

        Novel novel = new Novel();
        novel.setName(baseName);
        novel.setUploaderId(userId);
        novel.setFilePath(storedFile.storagePath());
        novel.setStorageType(storedFile.storageType());
        novel.setObjectKey(storedFile.storagePath());
        novel.setOriginalFilename(safeOriginalName);
        novel.setFileSize(storedFile.size());

        save(novel);
        return novel.getId();
    }

    private byte[] normalizeToUtf8(byte[] bytes) {
        String encoding = detectEncoding(bytes);
        String content = new String(bytes, Charset.forName(encoding));
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private String detectEncoding(byte[] bytes) {
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(bytes, 0, bytes.length);
        detector.dataEnd();
        String encoding = detector.getDetectedCharset();
        detector.reset();
        return encoding == null ? StandardCharsets.UTF_8.name() : encoding;
    }
}

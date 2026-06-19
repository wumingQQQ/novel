package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.mapper.ChapterMapper;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelService;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChapterService extends ServiceImpl<ChapterMapper, Chapter> implements IChapterService {

    private final INovelService novelService;
    private final IJobService jobService;

    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^第[一二三四五六七八九十百千\\d]+章[^。！？\n]*[。！？]?\\s*$",
            Pattern.MULTILINE
    );

    public ChapterService(INovelService novelService, IJobService jobService) {
        this.novelService = novelService;
        this.jobService = jobService;
    }

    @Override
    @Transactional
    public void splitChapter(int jobId) throws IOException {
        int novelId  = jobService.getById(jobId).getNovelId();
        // 幂等设计
        Long count = lambdaQuery().eq(Chapter::getNovelId, novelId).count();
        if(count > 0){
            return;
        }

        Novel novel = novelService.getById(novelId);
        String filePath = novel.getFilePath();
        Path path = Paths.get(filePath);

        String encoding = getEncoding(filePath);

        String content = Files.readString(path, Charset.forName(encoding));
        List<String> c = splitChapter(content);
        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < c.size(); i++) {
            String raw = c.get(i).trim();
            if(raw.isEmpty()) continue;   // 过滤空内容

            Chapter chapter = new Chapter();
            chapter.setNovelId(novelId);
            chapter.setSequence(i + 1);

            // 标题还需稍微改动
            String title = raw.lines().findFirst().orElse("").trim();
            chapter.setTitle(title);

            // 提取正文
            String body = raw.substring(title.length()).trim();
            chapter.setContent(body);

            chapters.add(chapter);
        }

        if(!chapters.isEmpty()) {
            saveBatch(chapters);
        }
    }

    private List<String> splitChapter(String content) {
        Matcher matcher = CHAPTER_PATTERN.matcher(content);
        List<String> result = new ArrayList<>();
        int prev = 0;
        while (matcher.find()) {
            // 保留第一个章节标题之前的内容
            if(matcher.start() > prev) {
                result.add(content.substring(prev, matcher.start()));
            }
            prev = matcher.start();
        }
        // 处理最后一个章节到文件末尾的内容
        if(prev > 0) result.add(content.substring(prev));
        else{
            // 章节标记匹配失败
            System.out.println("小说找不到章节标记");
            return Collections.emptyList();
        }
        return result;
    }



    private String getEncoding(String filePath) throws IOException {
        // 智能判断文件编码
        byte[] bytes = Files.readAllBytes(Paths.get(filePath));
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(bytes, 0, bytes.length);
        detector.dataEnd();
        String encoding = detector.getDetectedCharset();
        detector.reset();
        return encoding == null ? "UTF-8" : encoding;
    }
}

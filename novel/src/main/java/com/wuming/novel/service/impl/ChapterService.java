package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.infrastructure.mapper.ChapterMapper;
import com.wuming.novel.infrastructure.observability.TraceContext;
import com.wuming.novel.integration.message.EventPublisher;
import com.wuming.novel.integration.message.chaptersplit.ChapterSplitCompletedEvent;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelService;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

@Slf4j
@Service
public class ChapterService extends ServiceImpl<ChapterMapper, Chapter> implements IChapterService {

    private final ChapterMapper chapterMapper;
    private final INovelService novelService;
    private final IJobService jobService;
    private final EventPublisher<ChapterSplitCompletedEvent> eventPublisher;

    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^第[一二三四五六七八九十百千\\d]+章[^。！？\n]*[。！？]?\\s*$",
            Pattern.MULTILINE
    );

    public ChapterService(
            ChapterMapper chapterMapper,
            INovelService novelService,
            IJobService jobService,
            EventPublisher<ChapterSplitCompletedEvent> eventPublisher
    ) {
        this.chapterMapper = chapterMapper;
        this.novelService = novelService;
        this.jobService = jobService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void splitChapter(Long jobId){
        Job job = jobService.getById(jobId);
        if(job.getStage().getCode() >= JobStage.CHAPTER_SPLIT.getCode()){
            log.info("任务{}已经完成了阶段{}", jobId, JobStage.CHAPTER_SPLIT);
            return;
        }
        Long novelId  = job.getNovelId();

        try {
            // 幂等设计：清理旧数据重跑
            cleanOldChapter(novelId);

            Novel novel = novelService.getById(novelId);
            String filePath = novel.getFilePath();
            Path path = Paths.get(filePath);

            String encoding = getEncoding(filePath);

            String content = Files.readString(path, Charset.forName(encoding));
            List<String> c = splitChapter(content, novelId);
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
                publishChapterSplitCompletedEvents(job, chapters);
            }
            log.debug("job: {} 小说{}章节切分完成，编码: {}, 章节数: {}", jobId, novelId, encoding, chapters.size());
        } catch (Exception e) {
            log.error("job: {}章节切分失败", jobId, e);
            throw new RuntimeException("章节切分失败", e);
        }
    }

    private void publishChapterSplitCompletedEvents(Job job, List<Chapter> chapters) {
        Runnable publishTask = () -> chapters.forEach(chapter -> publishChapterSplitCompletedEvent(job, chapter));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishTask.run();
                }
            });
            return;
        }
        publishTask.run();
    }

    private void publishChapterSplitCompletedEvent(Job job, Chapter chapter) {
        ChapterSplitCompletedEvent event = new ChapterSplitCompletedEvent();
        event.setJobId(job.getId());
        event.setNovelId(job.getNovelId());
        event.setChapterId(chapter.getId());
        event.setChapterSequence(chapter.getSequence());
        event.setChapterTitle(chapter.getTitle());
        try (TraceContext.MdcScope ignoredJob = TraceContext.putJobId(job.getId());
             TraceContext.MdcScope ignoredNovel = TraceContext.putNovelId(job.getNovelId());
             TraceContext.MdcScope ignoredChapter = TraceContext.putChapterId(chapter.getId())) {
            eventPublisher.publish(event);
        } catch (RuntimeException e) {
            log.warn("章节切分完成事件发布失败，jobId: {}, novelId: {}, chapterId: {}",
                    job.getId(), job.getNovelId(), chapter.getId(), e);
        }
    }

    private void cleanOldChapter(Long novelId){
        QueryWrapper<Chapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("novel_id", novelId);
        int delete = chapterMapper.delete(queryWrapper);
        if(delete > 0) {
            log.info("清理小说{}的旧章节，数量为{}", novelId, delete);
        }
    }

    private List<String> splitChapter(String content, Long novelId) {
        Matcher matcher = CHAPTER_PATTERN.matcher(content);
        List<String> result = new ArrayList<>();
        int chapterStart = -1;
        while (matcher.find()) {
            // 不保留第一个章节标题之前的内容
            if(chapterStart >= 0) {
                result.add(content.substring(chapterStart, matcher.start()));
            }
            chapterStart = matcher.start();
        }
        // 处理最后一个章节到文件末尾的内容
        if(chapterStart >= 0) result.add(content.substring(chapterStart));
        else{
            // 章节标记匹配失败
            log.warn("小说{}找不到章节标记", novelId);
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

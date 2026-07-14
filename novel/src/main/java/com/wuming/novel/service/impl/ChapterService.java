package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.Chapter;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.infrastructure.mapper.ChapterMapper;
import com.wuming.novel.integration.storage.NovelFileStorageRouter;
import com.wuming.novel.passage.split.PassageSplitStrategyRouter;
import com.wuming.novel.passage.split.PassageSplitStrategyType;
import com.wuming.novel.service.IChapterService;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterService extends ServiceImpl<ChapterMapper, Chapter> implements IChapterService {

    private final ChapterMapper chapterMapper;
    private final INovelService novelService;
    private final IJobService jobService;
    private final NovelFileStorageRouter novelFileStorageRouter;
    private final PassageSplitStrategyRouter passageSplitStrategyRouter;

    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
            "^第[一二三四五六七八九十百千\\d]+章[^。！？\n]*[。！？]?\\s*$",
            Pattern.MULTILINE
    );


    /**
     * 按正则切分小说为章节
     * @param jobId 任务id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void splitChapter(Long jobId){
        Job job = jobService.getById(jobId);
        if(job.getStage().getCode() >= JobStage.CHAPTER_SPLIT.getCode()){
            log.debug("章节切分跳过，jobId: {}, currentStage: {}", jobId, job.getStage());
            return;
        }
        Long novelId  = job.getNovelId();

        try {
            // 幂等设计：清理旧数据重跑
            cleanOldChapter(novelId);

            Novel novel = novelService.getById(novelId);
            byte[] fileBytes = novelFileStorageRouter.read(novel);
            String content = new String(fileBytes, StandardCharsets.UTF_8);
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
                recordPassageSplitStrategy(novel, chapters);
            }
            log.info("章节切分完成，jobId: {}, novelId: {}, chapterCount: {}",
                    jobId, novelId, chapters.size());
        } catch (Exception e) {
            log.warn("章节切分失败，jobId: {}, novelId: {}, errorType: {}, errorMessage: {}",
                    jobId, novelId, e.getClass().getSimpleName(), e.getMessage());
            log.debug("章节切分异常堆栈，jobId: {}, novelId: {}", jobId, novelId, e);
            throw new RuntimeException("章节切分失败", e);
        }
    }

    /**
     * 在章节正则切分完成后，为整本小说固定Passage切分策略。
     */
    private void recordPassageSplitStrategy(Novel novel, List<Chapter> chapters) {
        PassageSplitStrategyType splitStrategy = passageSplitStrategyRouter.resolve(novel.getId(), chapters);
        novel.setPassageSplitStrategy(splitStrategy.name());
        novelService.updateById(novel);
        log.info("小说Passage切分策略已记录，novelId: {}, splitStrategy: {}", novel.getId(), splitStrategy);
    }

    /**
     * 清理之前小说分章的数据
     */
    private void cleanOldChapter(Long novelId){
        QueryWrapper<Chapter> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("novel_id", novelId);
        int delete = chapterMapper.delete(queryWrapper);
        if(delete > 0) {
            log.debug("清理旧章节完成，novelId: {}, deletedCount: {}", novelId, delete);
        }
    }

    /**
     * 正式开始切分
     * @param content 小说内容
     * @param novelId 便于调试的标记
     * @return 小说的各个章节
     */
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
            log.warn("章节切分未找到章节标记，novelId: {}", novelId);
            return Collections.emptyList();
        }
        return result;
    }

}

package com.wuming.novel.passage.split;

import com.wuming.novel.config.PassageSplitProperties;
import com.wuming.novel.domain.entity.Chapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 根据配置选择当前Passage切分策略。
 */
@Slf4j
@Component
public class PassageSplitStrategyRouter {
    private final PassageSplitProperties properties;
    private final Map<PassageSplitStrategyType, PassageSplitStrategy> strategies;

    public PassageSplitStrategyRouter(PassageSplitProperties properties, List<PassageSplitStrategy> strategies) {
        this.properties = properties;
        this.strategies = new EnumMap<>(PassageSplitStrategyType.class);
        for (PassageSplitStrategy strategy : strategies) {
            this.strategies.put(strategy.type(), strategy);
        }
    }

    /**
     * 返回当前配置启用的切分策略。
     */
    public PassageSplitStrategy current() {
        PassageSplitStrategyType type = properties.getSplitStrategy();
        if (type == PassageSplitStrategyType.AUTO) {
            throw new IllegalStateException("AUTO切分策略需要提供小说章节样本");
        }
        return strategy(type);
    }

    /**
     * 返回指定类型对应的切分策略。
     */
    public PassageSplitStrategy current(PassageSplitStrategyType type) {
        if (type == PassageSplitStrategyType.AUTO) {
            throw new IllegalArgumentException("AUTO不是可直接执行的Passage切分策略");
        }
        return strategy(type);
    }

    /**
     * 当前配置是否需要读取整本小说章节做策略判断。
     */
    public boolean requiresNovelSamples() {
        return properties.getSplitStrategy() == PassageSplitStrategyType.AUTO;
    }

    /**
     * 根据整本小说章节样本选择切分策略；非AUTO配置会直接返回指定策略。
     *
     * @param novelId 小说id，用作确定性随机抽样种子
     * @param chapters 小说章节列表
     * @return 本小说最终使用的切分策略
     */
    public PassageSplitStrategy current(Long novelId, List<Chapter> chapters) {
        return strategy(resolve(novelId, chapters));
    }

    /**
     * 根据当前配置和小说章节样本解析最终应记录到小说上的策略类型。
     */
    public PassageSplitStrategyType resolve(Long novelId, List<Chapter> chapters) {
        PassageSplitStrategyType configuredType = properties.getSplitStrategy();
        if (configuredType != PassageSplitStrategyType.AUTO) {
            return configuredType;
        }
        return resolveAutoStrategy(novelId, chapters);
    }

    /**
     * AUTO模式：随机抽样若干章节，并以多数票决定整本小说的切分策略。
     */
    private PassageSplitStrategyType resolveAutoStrategy(Long novelId, List<Chapter> chapters) {
        List<Chapter> samples = sampleChapters(novelId, chapters);
        long paragraphVotes = samples.stream()
                .filter(this::preferParagraphWindow)
                .count();
        long characterVotes = samples.size() - paragraphVotes;
        PassageSplitStrategyType resolvedType = paragraphVotes > characterVotes
                ? PassageSplitStrategyType.PARAGRAPH_WINDOW
                : PassageSplitStrategyType.CHARACTER_OVERLAP;
        log.debug("小说Passage切分策略自动选择完成，novelId: {}, sampleCount: {}, paragraphVotes: {}, characterVotes: {}, resolvedType: {}",
                novelId, samples.size(), paragraphVotes, characterVotes, resolvedType);
        return resolvedType;
    }

    /**
     * 使用novelId作为随机种子，保证同一本小说在不同章节构建时得到相同样本。
     */
    private List<Chapter> sampleChapters(Long novelId, List<Chapter> chapters) {
        if (chapters == null || chapters.isEmpty()) {
            return List.of();
        }
        int sampleChapterCount = properties.getAuto().getSampleChapterCount();
        if (sampleChapterCount <= 0) {
            throw new IllegalStateException("novel.passage.auto.sample-chapter-count 必须大于0");
        }
        List<Chapter> candidates = new ArrayList<>(chapters.stream()
                .filter(chapter -> chapter.getContent() != null && !chapter.getContent().isBlank())
                .sorted(Comparator.comparing(Chapter::getSequence, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Chapter::getId, Comparator.nullsLast(Long::compareTo)))
                .toList());
        if (candidates.size() <= sampleChapterCount) {
            return candidates;
        }
        candidates.sort(Comparator.comparingLong(chapter -> chapterSampleOrder(novelId, chapter)));
        return candidates.subList(0, sampleChapterCount);
    }

    /**
     * 判断单章是否更适合段落窗口切分。
     */
    private boolean preferParagraphWindow(Chapter chapter) {
        List<String> lines = nonBlankLines(chapter.getContent());
        if (lines.isEmpty()) {
            return false;
        }
        int paragraphCount = lines.size();
        double averageLineChars = lines.stream()
                .mapToInt(String::length)
                .average()
                .orElse(0);
        PassageSplitProperties.Auto auto = properties.getAuto();
        return paragraphCount > auto.getParagraphCountThreshold()
                || averageLineChars < auto.getAverageLineCharsThreshold();
    }

    private List<String> nonBlankLines(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private long chapterSampleOrder(Long novelId, Chapter chapter) {
        long seed = novelId == null ? 0L : novelId;
        seed = 31 * seed + (chapter.getId() == null ? 0L : chapter.getId());
        seed = 31 * seed + chapter.getSequence();
        return new Random(seed).nextLong();
    }

    private PassageSplitStrategy strategy(PassageSplitStrategyType type) {
        PassageSplitStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalStateException("未找到Passage切分策略: " + type);
        }
        return strategy;
    }
}

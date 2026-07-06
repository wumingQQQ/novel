package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.dto.RoleExampleExtractionResult;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.entity.PassageCharacter;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleExample;
import com.wuming.novel.infrastructure.mapper.RoleExampleMapper;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import com.wuming.novel.service.INovelPassageService;
import com.wuming.novel.service.INovelService;
import com.wuming.novel.service.IPassageCharacterService;
import com.wuming.novel.service.IRoleCharacterService;
import com.wuming.novel.service.IRoleExampleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 角色原作样本基础服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleExampleService extends ServiceImpl<RoleExampleMapper, RoleExample>
        implements IRoleExampleService {
    private static final String TEMPLATE_PATH = "prompts/role-example-extraction.st";
    private static final String BUILDING = "BUILDING";
    private static final String INCOMPLETE = "INCOMPLETE";
    private static final String VECTOR_PENDING = "PENDING";
    private static final Set<String> SAMPLE_TYPES = Set.of("INTERACTION", "NARRATION_EVAL");

    private final IRoleCharacterService roleCharacterService;
    private final INovelService novelService;
    private final INovelPassageService novelPassageService;
    private final IPassageCharacterService passageCharacterService;
    private final ChatClient chatClient;
    private final PromptTemplateRenderer renderer;
    @Lazy
    @Autowired
    private IRoleExampleService self;

    @Value("${novel.role-example.max-candidate-passages:80}")
    private int maxCandidatePassages;

    @Value("${novel.role-example.min-confidence:0.6}")
    private double minConfidence;

    @Override
    public int extractExamples(Long novelId, String characterName) {
        if (novelId == null) {
            throw new IllegalArgumentException("novelId不能为空");
        }
        if (characterName == null || characterName.isBlank()) {
            throw new IllegalArgumentException("characterName不能为空");
        }

        String normalizedCharacterName = characterName.trim();
        RoleCharacter character = getOrCreateCharacter(novelId, normalizedCharacterName);
        markBuilding(character);

        List<NovelPassage> candidates = candidatePassages(novelId, normalizedCharacterName);
        if (candidates.isEmpty()) {
            markIncomplete(character, "没有找到包含目标角色的候选Passage");
            return 0;
        }
        log.info("开始抽取角色原作样本，novelId: {}, characterId: {}, characterName: {}, candidateCount: {}",
                novelId, character.getId(), normalizedCharacterName, candidates.size());

        List<RoleExample> examples = extractExamples(character, candidates);
        if (examples.isEmpty()) {
            markIncomplete(character, "候选Passage中未抽取到有效角色样本");
            log.info("角色原作样本抽取完成，characterId: {}, characterName: {}, savedCount: 0",
                    character.getId(), normalizedCharacterName);
            return 0;
        }

        removeOldExamples(character.getId());
        self.saveBatch(examples);
        markIncomplete(character, "RoleExample已抽取，待构建ReactionRule和Profile");
        log.info("角色原作样本抽取完成，characterId: {}, characterName: {}, savedCount: {}",
                character.getId(), normalizedCharacterName, examples.size());
        return examples.size();
    }

    /**
     * 创建未存在角色或获取已存在角色
     * @param novelId 角色所在小说id
     * @param characterName 角色姓名
     * @return 角色
     */
    private RoleCharacter getOrCreateCharacter(Long novelId, String characterName) {
        RoleCharacter character = roleCharacterService.lambdaQuery()
                .eq(RoleCharacter::getNovelId, novelId)
                .eq(RoleCharacter::getCharacterName, characterName)
                .one();
        if (character != null) {
            // 存在则直接返回
            return character;
        }

        Novel novel = novelService.getById(novelId);
        if (novel == null) {
            throw new BusinessException(ErrorCode.NOVEL_NOT_FOUND);
        }

        // 不存在则新建
        RoleCharacter newCharacter = new RoleCharacter();
        newCharacter.setNovelId(novelId);
        newCharacter.setNovelName(novel.getName());
        newCharacter.setCharacterName(characterName);
        newCharacter.setBuildStatus(BUILDING);
        roleCharacterService.save(newCharacter);
        return newCharacter;
    }

    /**
     * 将角色构建状态标记为building
     * @param character 角色
     */
    private void markBuilding(RoleCharacter character) {
        character.setBuildStatus(BUILDING);
        character.setBuildError(null);
        character.setCompletedTime(null);
        roleCharacterService.updateById(character);
    }

    /**
     * 将角色构建状态标记为incomplete
     * @param character 角色
     * @param reason 未完成原因
     */
    private void markIncomplete(RoleCharacter character, String reason) {
        character.setBuildStatus(INCOMPLETE);
        character.setBuildError(reason);
        character.setCompletedTime(LocalDateTime.now());
        roleCharacterService.updateById(character);
    }

    /**
     * 根据角色名查询候选passage
     * @param novelId 小说id，用于查询时过滤
     * @param characterName 角色名
     * @return passage列表
     */
    private List<NovelPassage> candidatePassages(Long novelId, String characterName) {
        // 查询与角色名存在关联的passageId
        List<Long> matchedPassageIds = passageCharacterService.lambdaQuery()
                .eq(PassageCharacter::getCharacterName, characterName)
                .list()
                .stream()
                .map(PassageCharacter::getPassageId)
                .distinct()
                .toList();
        if (matchedPassageIds.isEmpty()) {
            return List.of();
        }

        // 获取某个passage中出现的角色数，少说明目标角色密度高
        Map<Long, Long> characterCounts = passageCharacterService.lambdaQuery()
                .in(PassageCharacter::getPassageId, matchedPassageIds)
                .list()
                .stream()
                .collect(Collectors.groupingBy(PassageCharacter::getPassageId, Collectors.counting()));

        // 正式查询passage
        Map<Long, NovelPassage> passages = novelPassageService.lambdaQuery()
                .eq(NovelPassage::getNovelId, novelId)
                .in(NovelPassage::getId, matchedPassageIds)
                .list()
                .stream()
                .collect(Collectors.toMap(NovelPassage::getId, passage -> passage, (left, right) -> left, LinkedHashMap::new));

        // 按照优先级对passage进行排序：角色数->书中全局顺序->插入数据库顺序(id)
        return matchedPassageIds.stream()
                .map(passages::get)
                .filter(passage -> passage != null && passage.getContent() != null && !passage.getContent().isBlank())
                .sorted(Comparator
                        .comparing((NovelPassage passage) -> characterCounts.getOrDefault(passage.getId(), Long.MAX_VALUE))
                        .thenComparing(NovelPassage::getSequence, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(NovelPassage::getId))
                .limit(Math.max(1, maxCandidatePassages))
                .toList();
    }

    /**
     * 移除某角色的旧sample
     * @param characterId 角色标识
     */
    private void removeOldExamples(Long characterId) {
        long oldCount = count(new LambdaQueryWrapper<RoleExample>()
                .eq(RoleExample::getCharacterId, characterId));
        if (oldCount > 0) {
            remove(new LambdaQueryWrapper<RoleExample>()
                    .eq(RoleExample::getCharacterId, characterId));
            log.debug("已清理角色旧样本，characterId: {}, oldCount: {}", characterId, oldCount);
        }
    }

    /**
     * 从批量passage中抽取样本
     * @param character 目标角色
     * @param candidates 候选文本块
     * @return 样本集合
     */
    private List<RoleExample> extractExamples(RoleCharacter character, List<NovelPassage> candidates) {
        Set<String> seen = new HashSet<>();
        return candidates.stream()
                .flatMap(passage -> extractOnePassage(character, passage).stream())
                .filter(example -> seen.add(example.getSampleType() + "\n" + example.getSampleText()))
                .toList();
    }

    /**
     * 针对单passage抽取样本
     * @param character 目标角色
     * @param passage 文本块
     * @return 样本集合
     */
    private List<RoleExample> extractOnePassage(RoleCharacter character, NovelPassage passage) {
        try {
            RoleExampleExtractionResult result = extractByLlm(character, passage);
            if (result == null || result.examples().isEmpty()) {
                log.debug("Passage未抽取到角色样本，characterId: {}, passageId: {}",
                        character.getId(), passage.getId());
                return List.of();
            }
            List<RoleExample> examples = result.examples().stream()
                    .map(example -> toRoleExample(character, passage, example))
                    .filter(Objects::nonNull)
                    .toList();
            log.debug("Passage角色样本抽取完成，characterId: {}, passageId: {}, extractedCount: {}",
                    character.getId(), passage.getId(), examples.size());
            return examples;
        } catch (RuntimeException e) {
            log.warn("Passage角色样本抽取失败，characterId: {}, characterName: {}, passageId: {}",
                    character.getId(), character.getCharacterName(), passage.getId(), e);
            return List.of();
        }
    }

    /**
     * 通过llm从文本块中抽取角色样本
     * @param character 目标角色
     * @param passage 文本块
     * @return 抽取结果
     */
    private RoleExampleExtractionResult extractByLlm(RoleCharacter character, NovelPassage passage) {
        String prompt = renderer.render(TEMPLATE_PATH, Map.of(
                "characterName", character.getCharacterName(),
                "passageContent", passage.getContent()
        ));

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(RoleExampleExtractionResult.class);
    }

    /**
     * 将llm响应集合中的单个对象转为角色样本
     * @param character 目标角色
     * @param passage 文本块
     * @param example 响应集合中的单个对象
     * @return 角色样本
     */
    private RoleExample toRoleExample(RoleCharacter character,
                                      NovelPassage passage,
                                      RoleExampleExtractionResult.Example example) {
        String sampleType = normalizeSampleType(example.type());
        String sampleText = normalizeSampleText(example.sampleText());
        double confidence = example.confidence() == null ? 0.0 : example.confidence();
        if (sampleType == null || sampleText == null || confidence < minConfidence) {
            return null;
        }

        // 创建角色样本
        RoleExample roleExample = new RoleExample();
        roleExample.setCharacterId(character.getId());
        roleExample.setCharacterName(character.getCharacterName());
        roleExample.setPassageId(passage.getId());
        roleExample.setSampleType(sampleType);
        roleExample.setSampleText(sampleText);
        roleExample.setConfidence(confidence);
        roleExample.setVectorStatus(VECTOR_PENDING);
        return roleExample;
    }

    // 确保sampleType内容在预期之内
    private String normalizeSampleType(String sampleType) {
        if (sampleType == null) {
            return null;
        }
        String normalized = sampleType.trim().toUpperCase();
        return SAMPLE_TYPES.contains(normalized) ? normalized : null;
    }

    // 归整sampleText
    private String normalizeSampleText(String sampleText) {
        if (sampleText == null) {
            return null;
        }
        String normalized = sampleText.trim();
        return normalized.isBlank() ? null : normalized;
    }
}

package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.entity.PassageCharacter;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleExample;
import com.wuming.novel.infrastructure.mapper.RoleExampleMapper;
import com.wuming.novel.integration.rpc.rag.RoleExampleVectorIndexService;
import com.wuming.novel.service.INovelPassageService;
import com.wuming.novel.service.INovelService;
import com.wuming.novel.service.IPassageCharacterService;
import com.wuming.novel.service.IRoleCharacterService;
import com.wuming.novel.service.IRoleExampleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 角色原作样本基础服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleExampleService extends ServiceImpl<RoleExampleMapper, RoleExample>
        implements IRoleExampleService {
    private static final String BUILDING = "BUILDING";
    private static final String INCOMPLETE = "INCOMPLETE";

    private final IRoleCharacterService roleCharacterService;
    private final INovelService novelService;
    private final INovelPassageService novelPassageService;
    private final IPassageCharacterService passageCharacterService;
    private final RoleExampleExtractor roleExampleExtractor;
    private final RoleExampleVectorIndexService roleExampleVectorIndexService;
    private final TransactionTemplate transactionTemplate;

    @Value("${novel.role-example.max-candidate-passages:80}")
    private int maxCandidatePassages;

    /**
     * 准备角色样本抽取阶段，确保角色存在并标记为构建中。
     *
     * @param novelId 小说id
     * @param characterName 角色名称
     * @return 角色id
     */
    @Override
    public Long startExampleExtraction(Long novelId, String characterName) {
        if (novelId == null) {
            throw new IllegalArgumentException("novelId不能为空");
        }
        if (characterName == null || characterName.isBlank()) {
            throw new IllegalArgumentException("characterName不能为空");
        }
        RoleCharacter character = getOrCreateCharacter(novelId, characterName.trim());
        markBuilding(character);
        return character.getId();
    }

    /**
     * 查询角色样本抽取候选Passage主键，用于Pipeline按Passage记录检查点。
     *
     * @param novelId 小说id
     * @param characterName 角色名称
     * @return 候选Passage主键列表
     */
    @Override
    public List<Long> candidatePassageIds(Long novelId, String characterName) {
        if (novelId == null) {
            throw new IllegalArgumentException("novelId不能为空");
        }
        if (characterName == null || characterName.isBlank()) {
            throw new IllegalArgumentException("characterName不能为空");
        }
        return candidatePassages(novelId, characterName.trim()).stream()
                .map(NovelPassage::getId)
                .toList();
    }

    /**
     * 抽取并重建单个Passage上的角色样本。
     *
     * <p>LLM抽取和向量索引不进入事务；当前Passage旧样本删除和新样本保存
     * 在独立事务中完成。</p>
     *
     * @param novelId 小说id
     * @param characterName 角色名称
     * @param passageId Passage主键
     * @return 样本抽取结果
     */
    @Override
    public ExtractExamplesResult extractExamplesFromPassage(Long novelId, String characterName, Long passageId) {
        if (novelId == null) {
            throw new IllegalArgumentException("novelId不能为空");
        }
        if (characterName == null || characterName.isBlank()) {
            throw new IllegalArgumentException("characterName不能为空");
        }
        if (passageId == null) {
            throw new IllegalArgumentException("passageId不能为空");
        }

        String normalizedCharacterName = characterName.trim();
        RoleCharacter character = getOrCreateCharacter(novelId, normalizedCharacterName);
        NovelPassage passage = novelPassageService.getById(passageId);
        if (passage == null || !Objects.equals(passage.getNovelId(), novelId)) {
            throw new IllegalArgumentException("Passage不存在或不属于当前小说: " + passageId);
        }
        List<RoleExample> examples = passage.getContent() == null || passage.getContent().isBlank()
                ? List.of()
                : roleExampleExtractor.extract(character, passage);
        return replacePassageExamples(novelId, character, passageId, examples);
    }

    /**
     * 完成角色样本抽取阶段后的角色状态标记。
     *
     * <p>阶段重试时本轮可能没有新增样本，因此以数据库中的角色样本总量判断是否已有有效结果。</p>
     *
     * @param novelId 小说id
     * @param characterName 角色名称
     * @param savedCount 本轮保存数量，仅保留给调用方表达本轮结果
     */
    @Override
    public void completeExampleExtraction(Long novelId, String characterName, int savedCount) {
        if (novelId == null) {
            throw new IllegalArgumentException("novelId不能为空");
        }
        if (characterName == null || characterName.isBlank()) {
            throw new IllegalArgumentException("characterName不能为空");
        }
        RoleCharacter character = getOrCreateCharacter(novelId, characterName.trim());
        long totalCount = count(new LambdaQueryWrapper<RoleExample>()
                .eq(RoleExample::getCharacterId, character.getId()));
        if (totalCount <= 0) {
            markIncomplete(character, "候选Passage中未抽取到有效角色样本");
            return;
        }
        markIncomplete(character, "RoleExample已抽取，待构建ReactionRule和Profile");
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
     * 在独立事务中替换单个Passage的样本，事务结束后再同步向量索引。
     */
    private ExtractExamplesResult replacePassageExamples(Long novelId,
                                                          RoleCharacter character,
                                                          Long passageId,
                                                          List<RoleExample> examples) {
        PersistedExample persisted = Objects.requireNonNull(transactionTemplate.execute(status -> {
            List<Long> oldExampleIds = removeOldExamples(character.getId(), passageId);
            if (!examples.isEmpty()) {
                saveBatch(examples);
            }
            List<Long> newExampleIds = examples.stream()
                    .map(RoleExample::getId)
                    .filter(Objects::nonNull)
                    .toList();
            return new PersistedExample(oldExampleIds, newExampleIds, examples.size());
        }));

        // TransactionTemplate返回后连接已释放，再执行可能耗时的远程索引操作。
        syncRoleExampleIndex(
                novelId,
                character,
                persisted.oldExampleIds(),
                persisted.newExampleIds(),
                persisted.exampleCount()
        );
        return new ExtractExamplesResult(character.getId(), persisted.exampleCount());
    }

    /**
     * 移除某角色在指定Passage上的旧sample。
     *
     * @param characterId 角色标识
     * @param passageId Passage主键
     * @return 已移除的样本主键
     */
    private List<Long> removeOldExamples(Long characterId, Long passageId) {
        List<Long> oldExampleIds = list(new LambdaQueryWrapper<RoleExample>()
                .select(RoleExample::getId)
                .eq(RoleExample::getCharacterId, characterId)
                .eq(RoleExample::getPassageId, passageId))
                .stream()
                .map(RoleExample::getId)
                .toList();
        if (oldExampleIds.isEmpty()) {
            return List.of();
        }
        remove(new LambdaQueryWrapper<RoleExample>()
                .eq(RoleExample::getCharacterId, characterId)
                .eq(RoleExample::getPassageId, passageId));
        log.debug("已清理角色Passage旧样本，characterId: {}, passageId: {}, oldCount: {}",
                characterId, passageId, oldExampleIds.size());
        return oldExampleIds;
    }

    /**
     * 删除旧角色样本向量并写入新样本向量。
     *
     * @param novelId 小说id
     * @param character 角色
     * @param oldExampleIds 待删除向量的旧样本id
     * @param newExampleIds 待写入向量的新样本id
     * @param exampleCount 本次保存的样本数量
     */
    private void syncRoleExampleIndex(Long novelId,
                                      RoleCharacter character,
                                      List<Long> oldExampleIds,
                                      List<Long> newExampleIds,
                                      int exampleCount) {
        if (newExampleIds.isEmpty() && exampleCount > 0) {
            throw new IllegalStateException("角色样本保存后未获取到样本主键，无法同步索引，characterId: "
                    + character.getId());
        }
        int deletedCount = roleExampleVectorIndexService.deleteByIds(character.getId(), oldExampleIds);
        requireRagSuccess("删除旧RoleExample向量", deletedCount);
        int indexedCount = roleExampleVectorIndexService.indexByIds(newExampleIds);
        requireRagSuccess("索引RoleExample向量", indexedCount);
        log.debug("角色样本向量同步索引完成，novelId: {}, characterId: {}, characterName: {}, deletedCount: {}, indexedCount: {}",
                novelId, character.getId(), character.getCharacterName(), deletedCount, indexedCount);
    }

    /**
     * 检查RAG调用结果，负数代表远程服务降级。
     *
     * @param action 当前动作
     * @param result RAG调用返回值
     */
    private void requireRagSuccess(String action, int result) {
        if (result < 0) {
            throw new IllegalStateException(action + "失败：RAG服务降级");
        }
    }

    /**
     * 角色样本落库结果，用于事务结束后同步向量索引。
     */
    private record PersistedExample(
            List<Long> oldExampleIds,
            List<Long> newExampleIds,
            int exampleCount
    ) {
    }
}

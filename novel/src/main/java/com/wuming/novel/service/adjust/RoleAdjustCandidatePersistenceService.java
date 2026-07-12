package com.wuming.novel.service.adjust;

import com.wuming.novel.domain.dto.RoleAdjustCandidate;
import com.wuming.novel.domain.entity.NovelPassage;
import com.wuming.novel.domain.entity.RoleAdjustEvidence;
import com.wuming.novel.domain.entity.RoleAdjustItem;
import com.wuming.novel.domain.enums.RoleAdjustChangeType;
import com.wuming.novel.domain.enums.RoleAdjustStatus;
import com.wuming.novel.infrastructure.mapper.RoleAdjustEvidenceMapper;
import com.wuming.novel.infrastructure.mapper.RoleAdjustItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RoleAdjustCandidatePersistenceService {
    private final RoleAdjustItemMapper itemMapper;
    private final RoleAdjustEvidenceMapper evidenceMapper;

    @Transactional(rollbackFor = Exception.class)
    public void saveCandidates(Long requestId, List<RoleAdjustCandidate> candidates,
                               List<NovelPassage> evidences, List<RoleAdjustGenerationService.BaselineAdjustment> baselineAdjustments) {
        if (requestId == null) {
            throw new IllegalArgumentException("requestId不能为空");
        }
        List<RoleAdjustCandidate> safeCandidates = candidates == null ? List.of() : candidates;
        Map<Integer, NovelPassage> passageMap = evidenceMap(evidences);
        Map<Integer, String> baselineAdjustmentIds = baselineAdjustmentIds(baselineAdjustments);

        for (int index = 0; index < safeCandidates.size(); index++) {
            RoleAdjustCandidate candidate = safeCandidates.get(index);
            validateCandidate(candidate);
            RoleAdjustItem item = toRoleAdjustItem(requestId, candidate, index + 1, baselineAdjustmentIds);
            itemMapper.insert(item);
            saveEvidence(item.getId(), candidate, passageMap);
        }
    }

    /**
     * 在落库边界校验 LLM 候选的最小可用字段，避免无效结构进入待评审列表。
     */
    private void validateCandidate(RoleAdjustCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("候选调整不能为空");
        }
        if (candidate.changeType() == null) {
            throw new IllegalArgumentException("候选调整类型不能为空");
        }
    }

    /**
     * 将本次生成上下文中的证据按 evidenceRef 编号映射，编号从 1 开始。
     */
    private Map<Integer, NovelPassage> evidenceMap(List<NovelPassage> evidences) {
        List<NovelPassage> safeEvidences = evidences == null ? List.of() : evidences;
        return IntStream.range(0, safeEvidences.size())
                .boxed()
                .collect(Collectors.toMap(
                        i -> i + 1,
                        safeEvidences::get
                ));
    }

    /**
     * 将基线补丁按 baselineRef 编号映射为稳定 adjustmentId。
     */
    private Map<Integer, String> baselineAdjustmentIds(
            List<RoleAdjustGenerationService.BaselineAdjustment> baselineAdjustments) {
        List<RoleAdjustGenerationService.BaselineAdjustment> safeAdjustments =
                baselineAdjustments == null ? List.of() : baselineAdjustments;
        return safeAdjustments.stream()
                .collect(Collectors.toMap(
                        RoleAdjustGenerationService.BaselineAdjustment::ref,
                        RoleAdjustGenerationService.BaselineAdjustment::adjustmentId
                ));
    }

    /**
     * 将 LLM 候选转换为待用户评审的持久化调整项。
     */
    private RoleAdjustItem toRoleAdjustItem(Long requestId, RoleAdjustCandidate candidate, int displayOrder,
                                            Map<Integer, String> baselineAdjustmentIds) {
        RoleAdjustItem item = new RoleAdjustItem();
        item.setRequestId(requestId);
        item.setChangeType(candidate.changeType());
        item.setTargetAdjustmentId(resolveTargetAdjustmentId(candidate, baselineAdjustmentIds));
        item.setApplicability(candidate.applicability());
        item.setExpectedBehavior(candidate.expectedBehavior());
        item.setForbiddenBehavior(candidate.forbiddenBehavior());
        item.setStatus(RoleAdjustStatus.PENDING);
        item.setDisplayOrder(displayOrder);
        return item;
    }

    /**
     * ADD 不绑定目标补丁；REPLACE/DISABLE 必须引用本次基线中的有效 baselineRef。
     */
    private String resolveTargetAdjustmentId(RoleAdjustCandidate candidate, Map<Integer, String> baselineAdjustmentIds) {
        if (candidate.changeType() == RoleAdjustChangeType.ADD) {
            return null;
        }
        Integer targetRef = candidate.targetRef();
        if (targetRef == null || !baselineAdjustmentIds.containsKey(targetRef)) {
            throw new IllegalArgumentException("候选调整引用了无效的基线补丁编号: " + targetRef);
        }
        return baselineAdjustmentIds.get(targetRef);
    }

    /**
     * 按候选返回的 evidenceRefs 生成候选项与一组原作 Passage 的关联。
     */
    private void saveEvidence(Long itemId, RoleAdjustCandidate candidate, Map<Integer, NovelPassage> passageMap) {
        if (itemId == null) {
            throw new IllegalStateException("候选调整项未生成主键，无法保存证据关联");
        }
        List<Integer> evidenceRefs = candidate.evidenceRefs();
        if (evidenceRefs == null || evidenceRefs.isEmpty()) {
            throw new IllegalArgumentException("候选调整缺少原作证据引用");
        }
        List<Long> passageIds = evidenceRefs.stream()
                .distinct()
                .map(evidenceRef -> {
                    NovelPassage passage = passageMap.get(evidenceRef);
                    if (passage == null) {
                        throw new IllegalArgumentException("候选调整引用了无效的原作证据编号: " + evidenceRef);
                    }
                    return passage.getId();
                })
                .toList();

        RoleAdjustEvidence evidence = new RoleAdjustEvidence();
        evidence.setItemId(itemId);
        evidence.setPassageIds(passageIds);
        evidenceMapper.insert(evidence);
    }
}

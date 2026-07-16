package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.dto.RoleProfileBuildResult;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleExample;
import com.wuming.novel.domain.entity.RoleProfile;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.infrastructure.mapper.RoleProfileMapper;
import com.wuming.novel.infrastructure.prompt.PromptTemplateRenderer;
import com.wuming.novel.llm.LlmConcurrencyLimiter;
import com.wuming.novel.service.IRoleCharacterService;
import com.wuming.novel.service.IRoleExampleService;
import com.wuming.novel.service.IRoleProfileService;
import com.wuming.novel.service.IRoleReactionRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 角色轻量画像摘要基础服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleProfileService
        extends ServiceImpl<RoleProfileMapper, RoleProfile>
        implements IRoleProfileService {
    private static final String SYSTEM_TEMPLATE_PATH = "prompts/system/role-profile-build.st";
    private static final String USER_TEMPLATE_PATH = "prompts/user/role-profile-build.st";
    private static final String BUILD_VERSION = "v1.0.0";
    private static final String COMPLETED = "COMPLETED";
    private static final String INCOMPLETE = "INCOMPLETE";
    private static final String INTERACTION = "INTERACTION";

    private final IRoleCharacterService roleCharacterService;
    private final IRoleExampleService roleExampleService;
    private final IRoleReactionRuleService roleReactionRuleService;
    private final ChatClient chatClient;
    private final PromptTemplateRenderer renderer;
    private final LlmConcurrencyLimiter llmConcurrencyLimiter;
    private final TransactionTemplate transactionTemplate;

    @Value("${novel.role-profile.min-example-confidence:0.8}")
    private double minExampleConfidence;

    @Value("${novel.role-profile.max-examples:50}")
    private int maxExamples;

    @Value("${novel.role-profile.min-profile-confidence:0.6}")
    private double minProfileConfidence;

    @Value("${novel.role-profile.completed-min-examples:30}")
    private int completedMinExamples;

    @Value("${novel.role-profile.completed-min-interactions:20}")
    private int completedMinInteractions;

    @Value("${novel.role-profile.completed-min-rules:5}")
    private int completedMinRules;

    @Override
    public boolean buildProfile(Long characterId) {
        if (characterId == null) {
            throw new IllegalArgumentException("characterId不能为空");
        }
        RoleCharacter character = roleCharacterService.getById(characterId);
        if (character == null) {
            throw new IllegalArgumentException("角色不存在: " + characterId);
        }

        List<RoleExample> examples = profileExamples(characterId);
        if (examples.isEmpty()) {
            persistProfileBuildResult(character, null, "没有足够的高置信角色样本用于构建Profile");
            log.debug("角色画像构建跳过，characterId: {}, reason: {}", characterId, "没有足够的高置信角色样本");
            return false;
        }
        List<RoleReactionRule> rules = roleReactionRules(characterId);

        log.debug("开始构建角色画像，characterId: {}, characterName: {}, exampleCount: {}, ruleCount: {}",
                characterId, character.getCharacterName(), examples.size(), rules.size());
        RoleProfileBuildResult result = llmConcurrencyLimiter.execute(() -> buildProfileByLlm(character, examples, rules));
        if (!isValidProfileResult(result)) {
            persistProfileBuildResult(character, null, "Profile构建失败或置信度不足");
            log.info("角色画像构建未达标，characterId: {}, confidence: {}",
                    characterId, result == null ? null : result.confidence());
            return false;
        }

        RoleProfile profile = toRoleProfile(character, result);
        persistProfileBuildResult(character, profile, null);
        log.debug("角色画像构建完成，characterId: {}, characterName: {}", characterId, character.getCharacterName());
        return true;
    }

    /**
     * 保存画像并刷新角色构建状态。
     *
     * @param character 角色
     * @param profile 画像，为空时只刷新失败状态
     * @param profileError 画像构建错误
     */
    private void persistProfileBuildResult(RoleCharacter character, RoleProfile profile, String profileError) {
        transactionTemplate.executeWithoutResult(status -> {
            if (profile != null) {
                remove(new LambdaQueryWrapper<RoleProfile>()
                        .eq(RoleProfile::getCharacterId, character.getId()));
                save(profile);
            }
            refreshCharacterBuildStatus(character.getId(), profileError);
        });
    }

    private List<RoleExample> profileExamples(Long characterId) {
        return roleExampleService.lambdaQuery()
                .eq(RoleExample::getCharacterId, characterId)
                .ge(RoleExample::getConfidence, minExampleConfidence)
                .orderByDesc(RoleExample::getConfidence)
                .last("limit " + Math.max(1, maxExamples))
                .list()
                .stream()
                .sorted(Comparator
                        .comparing(RoleExample::getSampleType, Comparator.nullsLast(String::compareTo))
                        .thenComparing(RoleExample::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    private List<RoleReactionRule> roleReactionRules(Long characterId) {
        return roleReactionRuleService.lambdaQuery()
                .eq(RoleReactionRule::getCharacterId, characterId)
                .orderByAsc(RoleReactionRule::getId)
                .list();
    }

    private RoleProfileBuildResult buildProfileByLlm(RoleCharacter character,
                                                     List<RoleExample> examples,
                                                     List<RoleReactionRule> rules) {
        PromptTemplateRenderer.DualPrompt dualPrompt = renderer.renderDual(
                SYSTEM_TEMPLATE_PATH,
                USER_TEMPLATE_PATH,
                Map.of(
                        "characterName", character.getCharacterName(),
                        "novelName", character.getNovelName(),
                        "examples", formatExamples(examples),
                        "reactionRules", formatReactionRules(rules)
                )
        );
        return chatClient.prompt()
                .system(dualPrompt.systemPrompt())
                .user(dualPrompt.userPrompt())
                .call()
                .entity(RoleProfileBuildResult.class);
    }

    private boolean isValidProfileResult(RoleProfileBuildResult result) {
        if (result == null) {
            return false;
        }
        double confidence = result.confidence() == null ? 0.0 : result.confidence();
        return confidence >= minProfileConfidence
                && result.coreTraits() != null
                && !result.coreTraits().isBlank()
                && result.speakingStyle() != null
                && result.speakingStyle().signature() != null
                && !result.speakingStyle().signature().isBlank();
    }

    private RoleProfile toRoleProfile(RoleCharacter character, RoleProfileBuildResult result) {
        RoleProfile profile = new RoleProfile();
        profile.setCharacterId(character.getId());
        profile.setCharacterName(character.getCharacterName());
        profile.setNovelId(character.getNovelId());
        profile.setNovelName(character.getNovelName());
        profile.setBasicInfo(toBasicInfo(result.basicInfo()));
        profile.setCoreTraits(trim(result.coreTraits()));
        profile.setSpeakingStyle(toSpeakingStyle(result.speakingStyle()));
        profile.setForbiddenBehaviors(String.join("\n", normalizeList(result.forbiddenBehaviors())));
        profile.setBuildVersion(BUILD_VERSION);
        return profile;
    }

    private RoleProfile.BasicInfo toBasicInfo(RoleProfileBuildResult.BasicInfo result) {
        RoleProfile.BasicInfo basicInfo = new RoleProfile.BasicInfo();
        if (result == null) {
            return basicInfo;
        }
        basicInfo.setAge(trim(result.age()));
        basicInfo.setGender(trim(result.gender()));
        basicInfo.setOccupation(trim(result.occupation()));
        basicInfo.setAppearance(trim(result.appearance()));
        return basicInfo;
    }

    private RoleProfile.SpeakingStyle toSpeakingStyle(RoleProfileBuildResult.SpeakingStyle result) {
        RoleProfile.SpeakingStyle speakingStyle = new RoleProfile.SpeakingStyle();
        if (result == null) {
            return speakingStyle;
        }
        speakingStyle.setSignature(trim(result.signature()));
        speakingStyle.setDistinctivePatterns(normalizeList(result.distinctivePatterns()));
        speakingStyle.setAvoidPatterns(normalizeList(result.avoidPatterns()));
        return speakingStyle;
    }

    private void refreshCharacterBuildStatus(Long characterId, String profileError) {
        RoleCharacter character = roleCharacterService.getById(characterId);
        if (character == null) {
            return;
        }

        long exampleCount = roleExampleService.lambdaQuery()
                .eq(RoleExample::getCharacterId, characterId)
                .count();
        long interactionCount = roleExampleService.lambdaQuery()
                .eq(RoleExample::getCharacterId, characterId)
                .eq(RoleExample::getSampleType, INTERACTION)
                .count();
        long ruleCount = roleReactionRuleService.lambdaQuery()
                .eq(RoleReactionRule::getCharacterId, characterId)
                .count();
        boolean profileExists = count(new LambdaQueryWrapper<RoleProfile>()
                .eq(RoleProfile::getCharacterId, characterId)) > 0;

        List<String> blockingReasons = new ArrayList<>();
        List<String> qualityWarnings = new ArrayList<>();
        if (exampleCount < completedMinExamples) {
            qualityWarnings.add("RoleExample数量不足: " + exampleCount + "/" + completedMinExamples);
        }
        if (interactionCount < completedMinInteractions) {
            qualityWarnings.add("INTERACTION样本数量不足: " + interactionCount + "/" + completedMinInteractions);
        }
        if (ruleCount < completedMinRules) {
            qualityWarnings.add("ReactionRule数量不足: " + ruleCount + "/" + completedMinRules);
        }
        if (!profileExists) {
            blockingReasons.add(profileError == null || profileError.isBlank() ? "RoleProfile未构建" : profileError);
        }

        if (blockingReasons.isEmpty()) {
            character.setBuildStatus(COMPLETED);
            character.setBuildError(qualityWarnings.isEmpty() ? null : String.join("；", qualityWarnings));
        } else {
            character.setBuildStatus(INCOMPLETE);
            List<String> reasons = new ArrayList<>(blockingReasons);
            reasons.addAll(qualityWarnings);
            character.setBuildError(String.join("；", reasons));
        }
        character.setCompletedTime(LocalDateTime.now());
        roleCharacterService.updateById(character);
    }

    private String formatExamples(List<RoleExample> examples) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < examples.size(); i++) {
            RoleExample example = examples.get(i);
            builder.append("样本").append(i + 1)
                    .append("，type=").append(example.getSampleType())
                    .append("，confidence=").append(example.getConfidence())
                    .append("：\n")
                    .append(example.getSampleText())
                    .append("\n\n");
        }
        return builder.toString();
    }

    private String formatReactionRules(List<RoleReactionRule> rules) {
        if (rules.isEmpty()) {
            return "暂无";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (RoleReactionRule rule : rules) {
            joiner.add(rule.getSituation() + "：" + rule.getRule());
        }
        return joiner.toString();
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::trim)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}

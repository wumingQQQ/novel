package com.wuming.novel.service.publicrole;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wuming.novel.domain.dto.RolePublicPageResponse;
import com.wuming.novel.domain.dto.RolePublicPreview;
import com.wuming.novel.domain.dto.RolePublicSummary;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleExample;
import com.wuming.novel.domain.entity.RoleProfile;
import com.wuming.novel.domain.entity.RoleReactionRule;
import com.wuming.novel.infrastructure.mapper.RoleCharacterMapper;
import com.wuming.novel.infrastructure.mapper.RoleExampleMapper;
import com.wuming.novel.infrastructure.mapper.RoleProfileMapper;
import com.wuming.novel.infrastructure.mapper.RoleReactionRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


/**
 * 提供公共角色大厅和预览页所需的脱敏只读数据。
 */
@Service
@RequiredArgsConstructor
public class RolePublicService {
    private static final String COMPLETED = "COMPLETED";
    private static final int MAX_PAGE_SIZE = 48;
    private static final int SUMMARY_MAX_LENGTH = 80;

    private final RoleCharacterMapper characterMapper;
    private final RoleProfileMapper profileMapper;
    private final RoleReactionRuleMapper ruleMapper;
    private final RoleExampleMapper exampleMapper;

    /**
     * 查询已完成构建的公共角色，并按完成时间倒序返回脱敏摘要。
     *
     * @param keyword 角色名或小说名关键词，可为空
     * @param page 从1开始的页码
     * @param size 每页数量，最大48
     * @return 公共角色摘要分页结果
     */
    public RolePublicPageResponse listCharacters(String keyword, int page, int size) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        String normalizedKeyword = trimToNull(keyword);
        LambdaQueryWrapper<RoleCharacter> wrapper = new LambdaQueryWrapper<RoleCharacter>()
                .eq(RoleCharacter::getBuildStatus, COMPLETED)
                .and(normalizedKeyword != null, condition -> condition
                        .like(RoleCharacter::getCharacterName, normalizedKeyword)
                        .or()
                        .like(RoleCharacter::getNovelName, normalizedKeyword))
                .orderByDesc(RoleCharacter::getCompletedTime)
                .orderByDesc(RoleCharacter::getId);
        Page<RoleCharacter> characterPage = characterMapper.selectPage(
                Page.of(normalizedPage, normalizedSize), wrapper);
        var items = characterPage.getRecords().stream()
                .map(this::toSummary)
                .toList();
        return new RolePublicPageResponse(items, characterPage.getTotal(), normalizedPage, normalizedSize);
    }

    /**
     * 查询一个已完成构建的公共角色预览。
     *
     * @param characterId 公共角色主键
     * @return 脱敏角色预览
     */
    public RolePublicPreview getPreview(Long characterId) {
        RoleCharacter character = characterMapper.selectById(characterId);
        if (character == null || !COMPLETED.equals(character.getBuildStatus())) {
            throw new IllegalArgumentException("公共角色不存在: " + characterId);
        }
        RolePublicSummary summary = toSummary(character);
        return new RolePublicPreview(
                summary.id(), summary.characterName(), summary.novelName(), summary.introduction(),
                summary.ruleCount(), summary.exampleCount(), summary.completedTime());
    }

    /**
     * 将内部角色资产映射为白名单字段，避免实体被直接暴露给调用方。
     */
    private RolePublicSummary toSummary(RoleCharacter character) {
        RoleProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<RoleProfile>()
                .eq(RoleProfile::getCharacterId, character.getId()));
        long ruleCount = ruleMapper.selectCount(new LambdaQueryWrapper<RoleReactionRule>()
                .eq(RoleReactionRule::getCharacterId, character.getId()));
        long exampleCount = exampleMapper.selectCount(new LambdaQueryWrapper<RoleExample>()
                .eq(RoleExample::getCharacterId, character.getId()));
        return new RolePublicSummary(
                character.getId(),
                character.getCharacterName(),
                character.getNovelName(),
                summarize(profile == null ? null : profile.getCoreTraits(), SUMMARY_MAX_LENGTH),
                ruleCount,
                exampleCount,
                character.getCompletedTime());
    }

    /**
     * 将内部性格描述截断为不可替代完整画像的简短导语。
     */
    private String summarize(String text, int maxLength) {
        String normalized = trimToNull(text);
        if (normalized == null) {
            return "角色档案正在整理中。";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 1) + "…";
    }

    /**
     * 规范化外部查询关键词。
     */
    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

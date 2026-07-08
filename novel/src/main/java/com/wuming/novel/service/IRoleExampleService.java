package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.RoleExample;

import java.util.List;

/**
 * 角色原作样本基础服务。
 */
public interface IRoleExampleService extends IService<RoleExample> {

    /**
     * 为指定小说角色抽取原作样本，替换该角色旧样本，并在事务提交后同步刷新样本向量索引。
     *
     * @param novelId 小说id
     * @param characterName 角色名称
     * @return 样本抽取结果
     */
    ExtractExamplesResult extractExamples(Long novelId, String characterName);

    /**
     * 查询指定角色的候选Passage主键。
     *
     * @param novelId 小说id
     * @param characterName 角色名称
     * @return 候选Passage主键列表
     */
    List<Long> candidatePassageIds(Long novelId, String characterName);

    /**
     * 重建单个Passage上的角色原作样本。
     *
     * @param novelId 小说id
     * @param characterName 角色名称
     * @param passageId Passage主键
     * @return 样本抽取结果
     */
    ExtractExamplesResult extractExamplesFromPassage(Long novelId, String characterName, Long passageId);

    /**
     * 标记角色样本抽取阶段已完成，后续等待反应规则和画像构建。
     *
     * @param novelId 小说id
     * @param characterName 角色名称
     * @param savedCount 本次阶段保存的样本数量
     */
    void completeExampleExtraction(Long novelId, String characterName, int savedCount);

    /**
     * 角色样本抽取结果。
     *
     * @param characterId 角色id
     * @param savedCount 本次保存的样本数量
     */
    record ExtractExamplesResult(Long characterId,
                                 int savedCount) {
    }
}

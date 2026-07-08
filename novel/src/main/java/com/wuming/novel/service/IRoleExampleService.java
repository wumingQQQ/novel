package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.RoleExample;

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
     * 角色样本抽取结果。
     *
     * @param characterId 角色id
     * @param savedCount 本次保存的样本数量
     */
    record ExtractExamplesResult(Long characterId,
                                 int savedCount) {
    }
}

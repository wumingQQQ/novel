package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.RoleCharacter;
import com.wuming.novel.domain.entity.RoleExample;

import java.util.List;

/**
 * 角色原作样本基础服务。
 */
public interface IRoleExampleService extends IService<RoleExample> {

    /**
     * 为指定小说角色抽取原作样本。
     *
     * @param novelId 小说id
     * @param characterName 角色名称
     * @return 本次新增的样本数
     */
    int extractExamples(Long novelId, String characterName);

    /**
     * 持久化已抽取的角色样本，并在事务提交后发布向量索引事件。
     *
     * @param novelId 小说id
     * @param character 角色
     * @param examples 已抽取样本
     * @return 本次保存的样本数
     */
    int persistExtractedExamples(Long novelId, RoleCharacter character, List<RoleExample> examples);
}

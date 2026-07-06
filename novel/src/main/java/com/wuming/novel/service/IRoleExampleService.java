package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.RoleExample;

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
}

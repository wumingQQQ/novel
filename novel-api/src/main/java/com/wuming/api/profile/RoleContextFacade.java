package com.wuming.api.profile;

import com.wuming.api.profile.dto.RoleContextDto;
import com.wuming.api.profile.dto.RoleContextResultDto;

public interface RoleContextFacade {
    /**
     * 获取指定任务的角色画像上下文
     * <P>该上下文用于消费者构造角色聊天提示词，包含小说信息、
     * 原作主角信息、目标角色画像与互动画像</P>
     * @param jobId 画像构建任务id
     * @return 角色聊天上下文
     * @throws IllegalArgumentException 当jobId为空或任务不存在时抛出
     * @throws IllegalStateException 当任务尚未完成时抛出
     */
    RoleContextResultDto getRoleContext(Long jobId);
}

package com.wuming.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wuming.chat.domain.entity.CharacterProfile;
import com.wuming.chat.domain.entity.InteractionProfile;
import com.wuming.chat.domain.entity.Job;
import com.wuming.chat.domain.entity.Novel;
import com.wuming.chat.domain.model.RoleProfileContext;
import com.wuming.chat.mapper.CharacterProfileMapper;
import com.wuming.chat.mapper.InteractionProfileMapper;
import com.wuming.chat.mapper.JobMapper;
import com.wuming.chat.mapper.NovelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileContextService {
    private final JobMapper jobMapper;
    private final NovelMapper novelMapper;
    private final CharacterProfileMapper characterProfileMapper;
    private final InteractionProfileMapper interactionProfileMapper;

    /**
     * 读取角色聊天所需的任务、小说、角色画像和互动画像。
     */
    public RoleProfileContext getProfileContext(Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("任务不存在: " + jobId);
        }
        Novel novel = novelMapper.selectById(job.getNovelId());
        if (novel == null) {
            throw new IllegalStateException("任务关联小说不存在，无法创建聊天会话");
        }

        CharacterProfile characterProfile = characterProfileMapper.selectOne(
                new LambdaQueryWrapper<CharacterProfile>()
                        .eq(CharacterProfile::getJobId, jobId)
        );
        InteractionProfile interactionProfile = interactionProfileMapper.selectOne(
                new LambdaQueryWrapper<InteractionProfile>()
                        .eq(InteractionProfile::getJobId, jobId)
        );
        if (characterProfile == null || interactionProfile == null) {
            throw new IllegalStateException("任务画像尚未生成完整，无法创建聊天会话");
        }

        return new RoleProfileContext(job, novel, characterProfile, interactionProfile);
    }
}

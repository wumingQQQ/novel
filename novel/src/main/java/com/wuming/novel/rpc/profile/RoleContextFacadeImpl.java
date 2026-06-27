package com.wuming.novel.rpc.profile;

import com.wuming.api.profile.RoleContextFacade;
import com.wuming.api.profile.dto.RoleContextDto;
import com.wuming.novel.domain.dto.FullPortraitDto;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.domain.entity.Novel;
import com.wuming.novel.domain.enums.JobStage;
import com.wuming.novel.service.IJobService;
import com.wuming.novel.service.INovelService;
import com.wuming.novel.serviceImpl.FullPortraitPersistenceService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
@RequiredArgsConstructor
public class RoleContextFacadeImpl implements RoleContextFacade {
    private final IJobService jobService;
    private final INovelService novelService;
    private final FullPortraitPersistenceService fullPortraitService;
    private final RoleContextAssembler roleContextAssembler;

    @Override
    public RoleContextDto getRoleContext(Long jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId不能为空");
        }
        Job job = jobService.getById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("job is null");
        }
        if(job.getStage() != JobStage.COMPLETE){
            throw new IllegalStateException("该job未完成");
        }

        Novel novel = novelService.getById(job.getNovelId());
        if(novel == null) {
            throw new IllegalStateException("任务关联小说不存在");
        }
        FullPortraitDto portraitDto = fullPortraitService.getByJobId(jobId);
        if(portraitDto == null) {
            throw new IllegalStateException("任务关联画像不存在");
        }

        return roleContextAssembler.toRoleContextDto(job, novel, portraitDto);
    }
}

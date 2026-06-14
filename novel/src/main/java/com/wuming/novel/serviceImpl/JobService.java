package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.Job;
import com.wuming.novel.mapper.JobMapper;
import com.wuming.novel.service.IJobService;
import org.springframework.stereotype.Service;

@Service
public class JobService extends ServiceImpl<JobMapper, Job> implements IJobService {
}

package com.wuming.novel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.wuming.novel.domain.entity.Evidence;

public interface IEvidenceService extends IService<Evidence> {
    boolean extractEvidence(int jobId);
}

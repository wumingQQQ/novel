package com.wuming.novel.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.Evidence;
import com.wuming.novel.mapper.EvidenceMapper;
import com.wuming.novel.service.IEvidenceService;
import org.springframework.stereotype.Service;

@Service
public class EvidenceService extends ServiceImpl<EvidenceMapper, Evidence> implements IEvidenceService {
}

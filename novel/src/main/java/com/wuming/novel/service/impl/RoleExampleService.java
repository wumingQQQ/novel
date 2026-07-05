package com.wuming.novel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.domain.entity.RoleExample;
import com.wuming.novel.infrastructure.mapper.RoleExampleMapper;
import com.wuming.novel.service.IRoleExampleService;
import org.springframework.stereotype.Service;

/**
 * 角色原作样本基础服务实现。
 */
@Service
public class RoleExampleService
        extends ServiceImpl<RoleExampleMapper, RoleExample>
        implements IRoleExampleService {
}

package com.wuming.novel.role.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wuming.novel.infrastructure.mapper.role.RoleExampleMapper;
import com.wuming.novel.role.entity.RoleExample;
import com.wuming.novel.role.service.IRoleExampleService;
import org.springframework.stereotype.Service;

/**
 * 角色原作样本基础服务实现。
 */
@Service
public class RoleExampleService
        extends ServiceImpl<RoleExampleMapper, RoleExample>
        implements IRoleExampleService {
}

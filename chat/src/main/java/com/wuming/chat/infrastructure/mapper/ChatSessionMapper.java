package com.wuming.chat.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wuming.chat.domain.dto.ChatSessionSummary;
import com.wuming.chat.domain.entity.ChatSession;

import java.util.List;

public interface ChatSessionMapper extends BaseMapper<ChatSession> {
    List<ChatSessionSummary> selectActiveSessionSummaries(Long userId);
}


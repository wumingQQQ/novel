package com.wuming.chat.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wuming.chat.domain.entity.ChatSessionMemory;
import org.apache.ibatis.annotations.Param;

/**
 * 会话长期记忆的持久化入口。
 */
public interface ChatSessionMemoryMapper extends BaseMapper<ChatSessionMemory> {

    /**
     * 使用版本号保护摘要更新，避免并发请求覆盖更新后的长期记忆。
     *
     * @param memory 待写入的新摘要与覆盖游标
     * @param expectedVersion 读取摘要时看到的版本号
     * @return 成功更新的记录数；为0表示摘要已被其他请求更新
     */
    int updateIfVersionMatches(@Param("memory") ChatSessionMemory memory,
                               @Param("expectedVersion") int expectedVersion);
}

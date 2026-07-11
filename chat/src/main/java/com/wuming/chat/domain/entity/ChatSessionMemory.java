package com.wuming.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一个会话当前有效的长期记忆摘要，以及它已覆盖到的原始消息位置。
 */
@Data
@TableName("chat_session_memories")
public class ChatSessionMemory {
    @TableId(value = "session_id", type = IdType.INPUT)
    private Long sessionId;
    private String summaryContent;
    private Long coveredMessageId;
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

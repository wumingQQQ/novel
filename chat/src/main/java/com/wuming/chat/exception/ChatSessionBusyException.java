package com.wuming.chat.exception;

import com.wuming.common.exception.BusinessException;
import com.wuming.common.exception.ErrorCode;

/**
 * 同一会话已有聊天回复正在生成时抛出的业务异常。
 */
public class ChatSessionBusyException extends BusinessException {

    public ChatSessionBusyException() {
        super(ErrorCode.CHAT_SESSION_BUSY);
    }
}

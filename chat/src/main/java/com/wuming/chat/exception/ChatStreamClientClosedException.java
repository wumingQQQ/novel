package com.wuming.chat.exception;

/**
 * 表示客户端已经主动断开聊天 SSE 连接，用于停止后续保存和发送。
 */
public class ChatStreamClientClosedException extends RuntimeException {
}

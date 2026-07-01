package com.wuming.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // 通用
    SUCCESS(0, "OK"),

    PARAM_ERROR(40000, "请求参数错误"),
    UNAUTHORIZED(40100, "未登录"),
    FORBIDDEN(40300, "无权限"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "资源状态冲突"),

    // novel
    NOVEL_FILE_UNSUPPORTED(41000, "不支持的小说格式"),
    NOVEL_FILE_TOO_LARGE(41001, "小说文件过大"),
    NOVEL_NOT_FOUND(41002, "小说不存在"),

    JOB_NOT_FOUND(41100, "任务不存在"),
    JOB_ALREADY_RUNNING(41101, "任务正在运行中"),
    JOB_NOT_RESTARTABLE(41102, "任务当前状态不可重启"),
    JOB_STAGE_FAILED(41103, "任务阶段执行失败"),

    LLM_EMPTY_RESPONSE(41200, "llm响应为空"),
    LLM_RESPONSE_PARSE_FAILED(41201, "llm响应解析失败"),
    LLM_RESPONSE_CHECK_FAILED(41202, "llm响应校验失败"),

    // user
    USER_NOT_FOUND(42000, "用户不存在"),
    USER_DISABLED(42001, "用户不可用"),

    // chat
    CHAT_SESSION_NOT_FOUND(43000, "聊天会话不存在"),
    CHAT_MESSAGE_EMPTY(43001, "聊天消息不能为空"),
    PROFILE_CONTEXT_NOT_FOUND(43002, "角色画像不存在"),
    RAG_RECALL_FAILED(43100, "rag召回失败"),


    REMOTE_SERVICE_ERROR(50200, "远程服务调用失败"),
    MESSAGE_SEND_ERROR(50300, "消息发送失败"),
    CACHE_OPERATION_FAILED(50400, "缓存操作失败"),
    SYSTEM_ERROR(50000, "系统内部异常");

    private final int code;
    private final String message;
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}

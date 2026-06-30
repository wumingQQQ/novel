package com.wuming.novel.integration.message;

public interface EventPublisher<T extends CompleteEvent> {
    /**
     * 发布事件完成消息到mq
     * @param event 具体完成事件对应的消息实体
     */
    void publish(T event);
}

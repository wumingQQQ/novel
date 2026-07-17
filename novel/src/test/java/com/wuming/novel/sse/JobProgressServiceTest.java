package com.wuming.novel.sse;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JobProgressServiceTest {

    @Test
    void completeJobRemovesSubscriberWhenProgressSendFailsAndCompletionAlsoFails() throws IOException {
        JobProgressStore jobProgressStore = mock(JobProgressStore.class);
        JobProgressService service = new JobProgressService(jobProgressStore);
        SseEmitter emitter = mock(SseEmitter.class);
        Long jobId = 1L;

        service.initJob(jobId);
        subscribers(service).put(jobId, emitter);
        doThrow(new IOException("connection closed"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        doThrow(new IllegalStateException("already completed")).when(emitter).complete();

        assertDoesNotThrow(() -> service.completeJob(jobId));

        assertFalse(subscribers(service).containsKey(jobId));
        verify(emitter, never()).complete();
    }

    @SuppressWarnings("unchecked")
    private Map<Long, SseEmitter> subscribers(JobProgressService service) {
        return (Map<Long, SseEmitter>) ReflectionTestUtils.getField(service, "subscribers");
    }
}

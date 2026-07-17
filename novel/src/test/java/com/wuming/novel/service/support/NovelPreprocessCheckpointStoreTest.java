package com.wuming.novel.service.support;

import com.wuming.novel.domain.enums.NovelPreprocessStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class NovelPreprocessCheckpointStoreTest {
    private static final long NOVEL_ID = 42L;
    private static final NovelPreprocessStage STAGE = NovelPreprocessStage.PASSAGE_BUILD;
    private static final String SUCCESS_KEY = "novel:42:PASSAGE_BUILD:success";
    private static final String FAILED_KEY = "novel:42:PASSAGE_BUILD:failed";
    private static final String ATTEMPTS_KEY = "novel:42:PASSAGE_BUILD:attempts";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private NovelPreprocessCheckpointStore checkpointStore;

    @BeforeEach
    void setUp() {
        checkpointStore = new NovelPreprocessCheckpointStore(redisTemplate);
    }

    @Test
    void selectedFailureRemainsRecoverableWhenWorkAbortsBeforeSuccess() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(FAILED_KEY, 0, -1)).thenReturn(List.of("2", "9", "2"));

        List<Long> selectedBeforeAbort = checkpointStore.selectItems(NOVEL_ID, STAGE, List.of(1L, 2L, 3L));
        List<Long> selectedForRetry = checkpointStore.selectItems(NOVEL_ID, STAGE, List.of(1L, 2L, 3L));

        assertEquals(List.of(2L), selectedBeforeAbort);
        assertEquals(List.of(2L), selectedForRetry);
        verify(redisTemplate, never()).delete(FAILED_KEY);
    }

    @Test
    void recordFailureKeepsExactlyOneRecoverableFailure() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);

        checkpointStore.recordFailure(NOVEL_ID, STAGE, 2L);

        org.mockito.InOrder order = inOrder(listOperations);
        order.verify(listOperations).remove(FAILED_KEY, 0, "2");
        order.verify(listOperations).rightPush(FAILED_KEY, "2");
    }

    @Test
    void selectItemsExcludesSucceededItemsWhenThereAreNoFailures() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(FAILED_KEY, 0, -1)).thenReturn(List.of());
        when(listOperations.range(SUCCESS_KEY, 0, -1)).thenReturn(List.of("2", "2"));

        List<Long> selected = checkpointStore.selectItems(NOVEL_ID, STAGE, List.of(1L, 2L, 3L));

        assertEquals(List.of(1L, 3L), selected);
    }

    @Test
    void selectItemsDoesNotRetryFailedItemsThatAlsoSucceeded() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(FAILED_KEY, 0, -1)).thenReturn(List.of("2"));
        when(listOperations.range(SUCCESS_KEY, 0, -1)).thenReturn(List.of("2"));

        List<Long> selected = checkpointStore.selectItems(NOVEL_ID, STAGE, List.of(1L, 2L, 3L));

        assertEquals(List.of(), selected);
    }

    @Test
    void recordSuccessAtomicallyRecordsSuccessAndRemovesFailure() {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(SUCCESS_KEY, FAILED_KEY)),
                eq("2")
        )).thenReturn(1L);

        checkpointStore.recordSuccess(NOVEL_ID, STAGE, 2L);

        verify(redisTemplate).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(SUCCESS_KEY, FAILED_KEY)),
                eq("2")
        );
        verifyNoInteractions(listOperations);
    }

    @Test
    void tryStartAttemptAllowsExactlyMaxAttempts() {
        when(redisTemplate.execute(org.mockito.ArgumentMatchers.<RedisScript<Long>>any(), eq(List.of(ATTEMPTS_KEY)), eq("3")))
                .thenReturn(1L, 1L, 1L, 0L);

        assertTrue(checkpointStore.tryStartAttempt(NOVEL_ID, STAGE, 3));
        assertTrue(checkpointStore.tryStartAttempt(NOVEL_ID, STAGE, 3));
        assertTrue(checkpointStore.tryStartAttempt(NOVEL_ID, STAGE, 3));
        assertFalse(checkpointStore.tryStartAttempt(NOVEL_ID, STAGE, 3));

        verify(redisTemplate, times(4)).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(ATTEMPTS_KEY)),
                eq("3")
        );
    }

    @Test
    void clearDeletesAllCheckpointKeys() {
        checkpointStore.clear(NOVEL_ID, STAGE);

        verify(redisTemplate).delete(SUCCESS_KEY);
        verify(redisTemplate).delete(FAILED_KEY);
        verify(redisTemplate).delete(ATTEMPTS_KEY);
    }
}

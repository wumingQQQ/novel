package com.wuming.novel.infrastructure.observability;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceContext {
    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";
    public static final String JOB_ID = "jobId";
    public static final String NOVEL_ID = "novelId";
    public static final String CHAPTER_ID = "chapterId";
    public static final String STAGE = "stage";
    public static final String TRACE_HEADER = "X-Trace-Id";

    private TraceContext() {
    }

    /**
     * 获取当前线程中的traceId。
     */
    public static String currentTraceId() {
        return MDC.get(TRACE_ID);
    }

    /**
     * 确保当前线程存在traceId，没有则生成一个新的。
     */
    public static String ensureTraceId() {
        String traceId = currentTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = newTraceId();
            MDC.put(TRACE_ID, traceId);
        }
        return traceId;
    }

    /**
     * 使用外部传入的traceId；为空时生成新的traceId。
     */
    public static String useTraceId(String traceId) {
        String value = traceId == null || traceId.isBlank() ? newTraceId() : traceId;
        MDC.put(TRACE_ID, value);
        return value;
    }

    /**
     * 向MDC写入指定字段，并返回可自动恢复旧值的作用域对象。
     */
    public static MdcScope put(String key, Object value) {
        if (value == null) {
            return new MdcScope(null, null, false);
        }
        String oldValue = MDC.get(key);
        boolean hadOldValue = oldValue != null;
        MDC.put(key, value.toString());
        return new MdcScope(key, oldValue, hadOldValue);
    }

    /**
     * 在当前线程日志上下文中记录用户id。
     */
    public static MdcScope putUserId(Long userId) {
        return put(USER_ID, userId);
    }

    /**
     * 在当前线程日志上下文中记录任务id。
     */
    public static MdcScope putJobId(Long jobId) {
        return put(JOB_ID, jobId);
    }

    /**
     * 在当前线程日志上下文中记录小说id。
     */
    public static MdcScope putNovelId(Long novelId) {
        return put(NOVEL_ID, novelId);
    }

    /**
     * 在当前线程日志上下文中记录章节id。
     */
    public static MdcScope putChapterId(Long chapterId) {
        return put(CHAPTER_ID, chapterId);
    }

    /**
     * 在当前线程日志上下文中记录当前任务阶段。
     */
    public static MdcScope putStage(Object stage) {
        return put(STAGE, stage);
    }

    /**
     * 清理当前线程中的所有日志上下文字段，避免线程池复用时串扰。
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * 生成不带横线的traceId，便于日志检索。
     */
    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static final class MdcScope implements AutoCloseable {
        private final String key;
        private final String oldValue;
        private final boolean hadOldValue;

        private MdcScope(String key, String oldValue, boolean hadOldValue) {
            this.key = key;
            this.oldValue = oldValue;
            this.hadOldValue = hadOldValue;
        }

        /**
         * 退出作用域时恢复旧MDC值，避免线程复用造成上下文串扰。
         */
        @Override
        public void close() {
            if (key == null) {
                return;
            }
            if (hadOldValue) {
                MDC.put(key, oldValue);
            } else {
                MDC.remove(key);
            }
        }
    }
}

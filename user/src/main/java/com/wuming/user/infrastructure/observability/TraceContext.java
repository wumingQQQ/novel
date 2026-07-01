package com.wuming.user.infrastructure.observability;

import com.wuming.common.trace.TraceConstants;
import org.slf4j.MDC;

import java.util.UUID;

public final class TraceContext {
    public static final String TRACE_ID = TraceConstants.TRACE_ID;
    public static final String USER_ID = TraceConstants.USER_ID;
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
     * 在当前线程日志上下文中记录用户id。
     */
    public static MdcScope putUserId(Long userId) {
        if (userId == null) {
            return new MdcScope(null, null, false);
        }
        String oldValue = MDC.get(USER_ID);
        boolean hadOldValue = oldValue != null;
        MDC.put(USER_ID, userId.toString());
        return new MdcScope(USER_ID, oldValue, hadOldValue);
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


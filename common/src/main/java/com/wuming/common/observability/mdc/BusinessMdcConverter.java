package com.wuming.common.observability.mdc;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.List;
import java.util.Map;

/**
 * 只为项目自身日志渲染非空MDC字段，避免第三方启动日志出现空业务上下文。
 */
public class BusinessMdcConverter extends ClassicConverter {

    private static final String BUSINESS_LOGGER_PREFIX = "com.wuming.";

    private static final List<String> DEFAULT_FIELDS = List.of(
            "traceId",
            "userId",
            "jobId",
            "novelId",
            "chapterId",
            "stage",
            "sessionId"
    );

    @Override
    public String convert(ILoggingEvent event) {
        if (!isBusinessLogger(event)) {
            return "";
        }
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc == null || mdc.isEmpty()) {
            return "";
        }
        return buildMdcText(mdc, resolveFields());
    }

    /**
     * 按logback配置的字段顺序输出非空MDC字段。
     */
    private String buildMdcText(Map<String, String> mdc, List<String> fields) {
        StringBuilder builder = new StringBuilder();
        for (String field : fields) {
            String value = mdc.get(field);
            if (hasText(value)) {
                builder.append(' ')
                        .append(field)
                        .append('=')
                        .append(value);
            }
        }
        return builder.toString();
    }

    private List<String> resolveFields() {
        List<String> options = getOptionList();
        if (options == null || options.isEmpty()) {
            return DEFAULT_FIELDS;
        }
        return options.stream()
                .map(String::trim)
                .filter(this::hasText)
                .toList();
    }

    private boolean isBusinessLogger(ILoggingEvent event) {
        String loggerName = event.getLoggerName();
        return loggerName != null
                && loggerName.startsWith(BUSINESS_LOGGER_PREFIX);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

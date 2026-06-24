package com.wuming.novel.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.BatchExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 输出 MyBatis 执行摘要日志。
 * 用于替代 mapper DEBUG 参数日志，保留数据库操作可见性，同时避免章节正文等大字段写入日志。
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SqlSummaryInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        SqlCommandType commandType = statement.getSqlCommandType();
        long start = System.currentTimeMillis();
        try {
            Object result = invocation.proceed();
            log.debug("sql summary: mapper={}, type={}, costMs={}, result={}",
                    statement.getId(),
                    commandType,
                    System.currentTimeMillis() - start,
                    getResultSummary(result));
            return result;
        } catch (Throwable e) {
            log.warn("sql summary: mapper={}, type={}, costMs={}, failed=true",
                    statement.getId(),
                    commandType,
                    System.currentTimeMillis() - start);
            throw e;
        }
    }

    private String getResultSummary(Object result) {
        if (result instanceof Collection<?> collection) {
            return String.valueOf(collection.size());
        }
        if (result instanceof Number number) {
            int value = number.intValue();
            if (value == BatchExecutor.BATCH_UPDATE_RETURN_VALUE) {
                return "BATCH";
            }
            return String.valueOf(value);
        }
        return result == null ? "0" : "1";
    }
}

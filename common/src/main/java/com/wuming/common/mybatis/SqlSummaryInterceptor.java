package com.wuming.common.mybatis;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Collection;

/**
 * 输出 MyBatis 执行摘要日志。
 * 用于替代 mapper DEBUG 参数日志，保留数据库操作可见性，同时避免章节正文等大字段写入日志。
 */
@Slf4j
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
            log.debug("sql summary: mapper={}, type={}, costMs={}",
                    statement.getId(),
                    commandType,
                    System.currentTimeMillis() - start
            );
            return result;
        } catch (Throwable e) {
            log.warn("sql summary: mapper={}, type={}, costMs={}, failed=true",
                    statement.getId(),
                    commandType,
                    System.currentTimeMillis() - start);
            throw e;
        }
    }

}

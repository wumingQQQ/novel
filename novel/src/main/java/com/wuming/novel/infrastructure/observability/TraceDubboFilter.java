package com.wuming.novel.infrastructure.observability;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.MDC;

@Activate(group = {CommonConstants.CONSUMER, CommonConstants.PROVIDER})
public class TraceDubboFilter implements Filter {

    /**
     * consumer侧传递traceId和userId，provider侧接收后写入MDC。
     * userId仅用于日志链路追踪，不作为权限判断依据。
     */
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (RpcContext.getServiceContext().isConsumerSide()) {
            attachTraceContext();
            return invoker.invoke(invocation);
        }

        String oldTraceId = MDC.get(TraceContext.TRACE_ID);
        String oldUserId = MDC.get(TraceContext.USER_ID);
        try {
            applyServerTraceContext();
            return invoker.invoke(invocation);
        } finally {
            restoreMdc(TraceContext.TRACE_ID, oldTraceId);
            restoreMdc(TraceContext.USER_ID, oldUserId);
        }
    }

    /**
     * 将当前线程的日志上下文写入Dubbo调用附件，传递给远端服务。
     */
    private void attachTraceContext() {
        RpcContext.getClientAttachment().setAttachment(
                TraceContext.TRACE_ID,
                TraceContext.ensureTraceId()
        );
        String userId = MDC.get(TraceContext.USER_ID);
        if (userId != null && !userId.isBlank()) {
            RpcContext.getClientAttachment().setAttachment(TraceContext.USER_ID, userId);
        }
    }

    /**
     * 从Dubbo调用附件恢复日志上下文，便于远端服务日志继续携带链路字段。
     */
    private void applyServerTraceContext() {
        String traceId = RpcContext.getServerAttachment()
                .getAttachment(TraceContext.TRACE_ID);
        TraceContext.useTraceId(traceId);

        String userId = RpcContext.getServerAttachment()
                .getAttachment(TraceContext.USER_ID);
        if (userId != null && !userId.isBlank()) {
            MDC.put(TraceContext.USER_ID, userId);
        }
    }

    /**
     * 恢复调用前的MDC字段，避免Dubbo线程复用造成上下文串扰。
     */
    private void restoreMdc(String key, String oldValue) {
        if (oldValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, oldValue);
        }
    }
}

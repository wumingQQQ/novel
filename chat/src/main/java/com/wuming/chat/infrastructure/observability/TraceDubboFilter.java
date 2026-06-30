package com.wuming.chat.infrastructure.observability;

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
     * consumer侧传递traceId，provider侧接收traceId并写入MDC。
     */
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (RpcContext.getServiceContext().isConsumerSide()) {
            RpcContext.getClientAttachment().setAttachment(
                    TraceContext.TRACE_ID,
                    TraceContext.ensureTraceId()
            );
            return invoker.invoke(invocation);
        }

        String oldTraceId = TraceContext.currentTraceId();
        try {
            String traceId = RpcContext.getServerAttachment()
                    .getAttachment(TraceContext.TRACE_ID);
            TraceContext.useTraceId(traceId);
            return invoker.invoke(invocation);
        } finally {
            if (oldTraceId == null) {
                MDC.remove(TraceContext.TRACE_ID);
            } else {
                MDC.put(TraceContext.TRACE_ID, oldTraceId);
            }
        }
    }
}


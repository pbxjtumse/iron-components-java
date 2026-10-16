package com.xjtu.iron.observability.starter;

import com.xjtu.iron.observability.api.tracing.ITraceService;
import com.xjtu.iron.observability.core.TraceMdc;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求级 MDC Filter。
 *
 * <p>职责：</p>
 * <ul>
 *     <li>在 HTTP 请求进入时，把当前 Trace 上下文中的 traceId/spanId 写入 MDC</li>
 *     <li>保证整个 HTTP 请求生命周期中的日志尽量都能带上 traceId/spanId</li>
 *     <li>请求结束后恢复进入前的 MDC，避免线程池复用导致日志污染</li>
 * </ul>
 *
 * <p>注意：</p>
 * <ul>
 *     <li>本 Filter 不负责创建请求级 Span</li>
 *     <li>HTTP Server Span 推荐由 OpenTelemetry Java Agent 自动创建</li>
 * </ul>
 */
public class ObservabilityWebFilter extends OncePerRequestFilter {

    private final ITraceService traceService;

    public ObservabilityWebFilter(ITraceService traceService) {
        this.traceService = traceService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        TraceMdc.MdcScope mdcScope = null;

        try {
            mdcScope = TraceMdc.put(traceService);

            setTraceResponseHeader(response);

            filterChain.doFilter(request, response);
        } finally {
            setTraceResponseHeader(response);

            if (mdcScope != null) {
                mdcScope.close();
            }
        }
    }

    private void setTraceResponseHeader(HttpServletResponse response) {
        if (response.isCommitted()) {
            return;
        }

        String traceId = TraceMdc.currentTraceId();
        if (isBlank(traceId) && traceService != null) {
            traceId = traceService.traceId();
        }

        if (!isBlank(traceId)) {
            response.setHeader("X-Trace-Id", traceId);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
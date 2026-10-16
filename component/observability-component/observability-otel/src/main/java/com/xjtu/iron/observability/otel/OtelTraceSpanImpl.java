package com.xjtu.iron.observability.otel;

import com.xjtu.iron.observability.api.tracing.ITraceSpan;
import com.xjtu.iron.observability.api.tracing.resolver.TraceErrorResolver;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OpenTelemetry Span 适配器。
 *
 * <p>职责：</p>
 * <ul>
 *     <li>把 ITraceSpan 适配成 OpenTelemetry Span</li>
 *     <li>负责 tag 类型转换</li>
 *     <li>负责异常基础记录</li>
 *     <li>负责调用 TraceErrorResolver 扩展异常解析</li>
 *     <li>负责关闭 Scope 和结束 Span</li>
 * </ul>
 */
public class OtelTraceSpanImpl implements ITraceSpan {

    private final Span span;

    private final Scope scope;

    private final List<TraceErrorResolver> errorResolvers;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public OtelTraceSpanImpl(Span span, Scope scope) {
        this(span, scope, Collections.emptyList());
    }

    public OtelTraceSpanImpl(Span span, Scope scope, List<TraceErrorResolver> errorResolvers) {
        this.span = Objects.requireNonNull(span, "span must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.errorResolvers = errorResolvers == null ? Collections.emptyList() : errorResolvers;
    }

    @Override
    public void tag(String key, String value) {
        if (isBlank(key) || value == null) {
            return;
        }

        span.setAttribute(key, value);
    }

    @Override
    public void tag(String key, Number value) {
        if (isBlank(key) || value == null) {
            return;
        }

        if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long) {
            span.setAttribute(key, value.longValue());
            return;
        }

        if (value instanceof Float || value instanceof Double) {
            span.setAttribute(key, value.doubleValue());
            return;
        }

        span.setAttribute(key, value.toString());
    }

    @Override
    public void tag(String key, Boolean value) {
        if (isBlank(key) || value == null) {
            return;
        }

        span.setAttribute(key, value);
    }

    @Override
    public void tag(String key, Object value) {
        if (isBlank(key) || value == null) {
            return;
        }

        if (value instanceof String v) {
            tag(key, v);
            return;
        }

        if (value instanceof Number v) {
            tag(key, v);
            return;
        }

        if (value instanceof Boolean v) {
            tag(key, v);
            return;
        }

        /*
         * 复杂对象不要直接序列化成大 JSON。
         * 这里直接 String.valueOf，避免敏感字段和性能问题。
         */
        span.setAttribute(key, String.valueOf(value));
    }

    @Override
    public void error(Throwable throwable) {
        if (throwable == null) {
            return;
        }

        /*
         * 1. OpenTelemetry 标准异常记录。
         */
        span.recordException(throwable);

        /*
         * 2. 第一阶段默认把异常 Span 标记为 ERROR。
         *
         * 注意：
         * 业务异常是否一定算 ERROR，后续可以通过 ErrorPolicy 再升级。
         * 当前阶段先保证异常一定能看见。
         */
        span.setStatus(StatusCode.ERROR, safeMessage(throwable));

        /*
         * 3. 写入通用异常标签。
         */
        tag("exception.class", throwable.getClass().getName());
        tag("exception.message", safeMessage(throwable));

        Throwable rootCause = rootCause(throwable);
        if (rootCause != throwable) {
            tag("exception.root.class", rootCause.getClass().getName());
            tag("exception.root.message", safeMessage(rootCause));
        }

        /*
         * 4. 调用业务项目自定义异常解析器。
         */
        applyErrorResolvers(throwable);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        /*
         * 对应官方常见写法：
         *
         * Span span = tracer.spanBuilder("xxx").startSpan();
         * try (Scope scope = span.makeCurrent()) {
         *     ...
         * } finally {
         *     span.end();
         * }
         */
        try {
            scope.close();
        } finally {
            span.end();
        }
    }

    private void applyErrorResolvers(Throwable throwable) {
        if (errorResolvers.isEmpty()) {
            tag("error.resolved", false);
            return;
        }

        for (TraceErrorResolver resolver : errorResolvers) {
            if (resolver == null) {
                continue;
            }

            try {
                if (resolver.supports(throwable)) {
                    resolver.resolve(throwable, this);
                    tag("error.resolved", true);
                    tag("error.resolver", resolver.getClass().getName());
                    return;
                }
            } catch (Throwable resolverError) {
                /*
                 * Resolver 自己失败，绝不能影响业务主流程。
                 */
                tag("error.resolved", false);
                tag("error.resolver.failed", true);
                tag("error.resolver.class", resolver.getClass().getName());
                tag("error.resolver.message", safeMessage(resolverError));
                return;
            }
        }

        tag("error.resolved", false);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) {
            return "";
        }
        return throwable.getMessage();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
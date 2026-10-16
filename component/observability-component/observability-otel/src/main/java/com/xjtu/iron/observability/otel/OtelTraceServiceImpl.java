package com.xjtu.iron.observability.otel;

import com.xjtu.iron.observability.api.tracing.ITraceService;
import com.xjtu.iron.observability.api.tracing.ITraceSpan;
import com.xjtu.iron.observability.api.tracing.resolver.TraceErrorResolver;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.Collections;
import java.util.List;

/**
 * OpenTelemetry 版 ITraceService 实现。
 *
 * <p>职责：</p>
 * <ul>
 *     <li>通过 OpenTelemetry Tracer 创建 Span</li>
 *     <li>把 Span 设置为当前上下文</li>
 *     <li>把自定义 TraceErrorResolver 传递给 OtelTraceSpan</li>
 * </ul>
 */
public class OtelTraceServiceImpl implements ITraceService {

    private static final String DEFAULT_INSTRUMENTATION_NAME = "xjtu-iron-observability";

    private final Tracer tracer;

    private final List<TraceErrorResolver> errorResolvers;

    public OtelTraceServiceImpl(String instrumentationName) {
        this(instrumentationName, Collections.emptyList());
    }

    public OtelTraceServiceImpl(String instrumentationName, List<TraceErrorResolver> errorResolvers) {
        String realInstrumentationName = isBlank(instrumentationName)
                ? DEFAULT_INSTRUMENTATION_NAME
                : instrumentationName;

        this.tracer = GlobalOpenTelemetry.getTracer(realInstrumentationName);
        this.errorResolvers = errorResolvers == null ? Collections.emptyList() : errorResolvers;
    }

    @Override
    @SuppressWarnings("MustBeClosed")
    public ITraceSpan startSpan(String spanName) {
        String realSpanName = isBlank(spanName) ? "unknown" : spanName;

        Span span = tracer.spanBuilder(realSpanName).startSpan();

        /*
         * 非常关键：
         * makeCurrent() 之后，Span.current() 才能拿到当前 Span。
         * traceId() / spanId() 才能取到值。
         */
        Scope scope = span.makeCurrent();

        return new OtelTraceSpanImpl(span, scope, errorResolvers);
    }

    @Override
    public String traceId() {
        SpanContext spanContext = Span.current().getSpanContext();
        return spanContext.isValid() ? spanContext.getTraceId() : "";
    }

    @Override
    public String spanId() {
        SpanContext spanContext = Span.current().getSpanContext();
        return spanContext.isValid() ? spanContext.getSpanId() : "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
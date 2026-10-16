package com.xjtu.iron.observability.core;

import com.xjtu.iron.observability.api.tracing.ITraceService;
import com.xjtu.iron.observability.api.tracing.ITraceSpan;
import com.xjtu.iron.observability.api.tracing.template.ITraceCallback;
import com.xjtu.iron.observability.api.tracing.template.ITraceRunnable;
import com.xjtu.iron.observability.api.tracing.template.ITraceSpanCallback;
import com.xjtu.iron.observability.core.noop.NoopTraceSpan;

import java.util.Map;
import java.util.Objects;

/**
 * Trace 模板类。
 *
 * <p>用于统一封装：</p>
 * <pre>
 * startSpan
 * put MDC
 * try
 * catch
 * record error
 * close span
 * restore MDC
 * </pre>
 *
 * <p>核心原则：可观测失败不能影响业务主流程。</p>
 */
public class TraceTemplate {

    private final ITraceService traceService;

    /**
     * 是否启用模板级 MDC。
     *
     * <p>开启后，TraceTemplate 创建代码块级 Span 后，
     * 会把该 Span 的 traceId/spanId 临时写入 MDC。</p>
     */
    private final boolean mdcEnabled;

    public TraceTemplate(ITraceService traceService) {
        this(traceService, true);
    }

    public TraceTemplate(ITraceService traceService, boolean mdcEnabled) {
        this.traceService = Objects.requireNonNull(traceService, "traceService must not be null");
        this.mdcEnabled = mdcEnabled;
    }

    public <T, E extends Throwable> T execute(String spanName, ITraceCallback<T, E> callback) throws E {
        ITraceSpan span = safeStartSpan(spanName);
        TraceMdc.MdcScope mdcScope = safePutMdc();

        try {
            return callback.execute();
        } catch (Throwable e) {
            safeRecordError(span, e);
            throw e;
        } finally {
            close(span, mdcScope);
        }
    }

    /**
     * 执行一段有返回值的业务逻辑，并自动创建 Span，回调函数中可以拿到当前 Span 对象
     * 比普通 callback 多了一个能力：可以加业务标签。
     * @param spanName Span名称
     * @param callback 执行业务逻辑 具有返回值
     * @param <T> 结果T
     * @param <E> 异常类型
     * @return 执行结果
     * @throws E 异常类型
     */
    public <T, E extends Throwable> T execute(String spanName, ITraceSpanCallback<T, E> callback) throws E {
        ITraceSpan span = safeStartSpan(spanName);
        TraceMdc.MdcScope mdcScope = safePutMdc();

        try {
            return callback.execute(span);
        } catch (Throwable e) {
            safeRecordError(span, e);
            throw e;
        } finally {
            close(span, mdcScope);
        }
    }

    /**
     * 执行一段有返回值的业务逻辑，没有返回值
     * @param spanName Span名称
     * @param runnable 执行业务逻辑 没有返回值
     * @param <E> 异常类型
     * @throws E 异常类型
     */
    public <E extends Throwable> void executeWithoutResult(String spanName, ITraceRunnable<E> runnable) throws E {
        ITraceSpan span = safeStartSpan(spanName);
        TraceMdc.MdcScope mdcScope = safePutMdc();

        try {
            runnable.run();
        } catch (Throwable e) {
            safeRecordError(span, e);
            throw e;
        } finally {
            close(span, mdcScope);
        }
    }

    public <T, E extends Throwable> T execute(
            String spanName,
            Map<String, Object> tags,
            ITraceCallback<T, E> callback
    ) throws E {
        ITraceSpan span = safeStartSpan(spanName);
        TraceMdc.MdcScope mdcScope = safePutMdc();

        try {
            putTags(span, tags);
            return callback.execute();
        } catch (Throwable e) {
            safeRecordError(span, e);
            throw e;
        } finally {
            close(span, mdcScope);
        }
    }

    private ITraceSpan safeStartSpan(String spanName) {
        try {
            ITraceSpan span = traceService.startSpan(spanName);
            return span == null ? NoopTraceSpan.INSTANCE : span;
        } catch (Throwable ignored) {
            return NoopTraceSpan.INSTANCE;
        }
    }

    private TraceMdc.MdcScope safePutMdc() {
        if (!mdcEnabled) {
            return TraceMdc.MdcScope.noop();
        }

        try {
            TraceMdc.MdcScope scope = TraceMdc.put(traceService);
            return scope == null ? TraceMdc.MdcScope.noop() : scope;
        } catch (Throwable ignored) {
            return TraceMdc.MdcScope.noop();
        }
    }

    private void safeRecordError(ITraceSpan span, Throwable throwable) {
        try {
            if (span != null) {
                span.error(throwable);
            }
        } catch (Throwable ignored) {
            // tracing 失败不能影响业务异常继续抛出
        }
    }

    private void putTags(ITraceSpan span, Map<String, Object> tags) {
        if (span == null || tags == null || tags.isEmpty()) {
            return;
        }

        tags.forEach(span::tag);
    }

    private void close(ITraceSpan span, TraceMdc.MdcScope mdcScope) {
        try {
            if (span != null) {
                span.close();
            }
        } finally {
            if (mdcScope != null) {
                mdcScope.close();
            }
        }
    }
}
package com.xjtu.iron.observability.core.noop;

import com.xjtu.iron.observability.api.tracing.ITraceSpan;

/**
 * 空实现 Span。
 *
 * <p>用于 tracing 不可用、关闭、异常降级等场景。</p>
 *
 * <p>它什么都不做，但可以保证业务流程继续执行。</p>
 */
public enum NoopTraceSpan implements ITraceSpan {

    /** 全局共享的无操作 Span 单例。 */
    INSTANCE;

    @Override
    public void tag(String key, String value) {
    }

    @Override
    public void tag(String key, Number value) {
    }

    @Override
    public void tag(String key, Boolean value) {
    }

    @Override
    public void tag(String key, Object value) {
    }

    @Override
    public void error(Throwable throwable) {
    }

    @Override
    public void close() {
    }
}

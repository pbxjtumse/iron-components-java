package com.xjtu.iron.relational.spi.execution;

import java.time.Duration;

/**
 * SQL 执行生命周期监听扩展点。
 *
 * <p>后续 Micrometer / Trace / Slow SQL 通过该 SPI 接入；Relational Core 不直接绑定
 * Micrometer 或 OpenTelemetry。监听器属于旁路能力，不应通过抛异常改变 SQL 主链结果。</p>
 */
public interface SqlExecutionListener {

    default void beforeExecute(SqlExecutionContext context) {
    }

    default void afterSuccess(SqlExecutionContext context, Duration elapsed) {
    }

    default void afterFailure(SqlExecutionContext context, Duration elapsed, Throwable failure) {
    }
}

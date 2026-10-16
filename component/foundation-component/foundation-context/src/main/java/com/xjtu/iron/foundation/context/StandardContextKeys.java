package com.xjtu.iron.foundation.context;

/**
 * Foundation 预定义上下文键。
 */
public final class StandardContextKeys {

    public static final ContextKey<String> REQUEST_ID = ContextKey.of("requestId", String.class);
    public static final ContextKey<String> CORRELATION_ID = ContextKey.of("correlationId", String.class);
    public static final ContextKey<String> TENANT_ID = ContextKey.of("tenantId", String.class);
    public static final ContextKey<String> OPERATOR_ID = ContextKey.of("operatorId", String.class);

    private StandardContextKeys() {}
}

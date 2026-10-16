package com.xjtu.iron.observability.api.tracing;
import java.lang.annotation.*;

/**
 * 方法级链路追踪注解。
 *
 * <p>用于标记关键业务方法，后续由 starter 模块中的 AOP 切面识别，
 * 自动创建 Span，记录方法耗时、异常、基础标签等信息。</p>
 *
 * <p>典型使用场景：</p>
 * <pre>
 * Trace("order.create")
 * public OrderResult createOrder(CreateOrderCmd cmd) {
 *     ...
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Trace {
    /**
     * Span 名称。
     *
     * <p>如果为空，框架可以默认使用：</p>
     * <pre>
     * ClassName.methodName
     * </pre>
     *
     * <p>建议核心业务方法显式指定，例如：</p>
     * <pre>
     * order.create
     * payment.submit
     * inventory.lock
     * </pre>
     */
    String value() default "";

    /**
     * 是否记录方法参数信息。
     *
     * <p>注意：第一阶段建议只记录参数数量，不记录参数内容。
     * 因为参数中可能包含手机号、身份证、token、密码等敏感信息。</p>
     */
    boolean recordArgs() default false;

    /**
     * 是否记录异常信息。
     *
     * <p>默认记录。后续 AOP 捕获异常后，会调用 ITraceSpan#error(Throwable)。</p>
     */
    boolean recordException() default true;

    /**
     * 是否启用当前注解。
     *
     * <p>用于临时关闭某个方法的 tracing。</p>
     */
    boolean enabled() default true;
}

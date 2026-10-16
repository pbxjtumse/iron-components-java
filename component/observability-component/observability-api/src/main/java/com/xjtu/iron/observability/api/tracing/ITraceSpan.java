package com.xjtu.iron.observability.api.tracing;

/**
 * 链路追踪节点抽象。
 *
 * <p>一个 ITraceSpan 表示一次链路中的一个节点。</p>
 *
 * <p>它可能对应：</p>
 * <ul>
 *     <li>OpenTelemetry Span</li>
 *     <li>SkyWalking Span，后续扩展</li>
 *     <li>Noop 空 Span</li>
 *     <li>公司自研 tracing 节点</li>
 * </ul>
 *
 * <p>业务代码不应该关心底层具体是哪种实现。</p>
 *
 * <p>典型用法：</p>
 * <pre>
 * try (ITraceSpan span = traceService.startSpan("order.create")) {
 *     span.tag("order.id", orderId);
 *     span.tag("order.channel", "douyin");
 *     ...
 * }
 * </pre>
 *
 * <p>注意：</p>
 * <ul>
 *     <li>tag 只应该记录有排查价值的轻量字段</li>
 *     <li>不要记录密码、token、身份证、银行卡等敏感信息</li>
 *     <li>不要记录大对象或完整请求体</li>
 *     <li>error(Throwable) 只记录异常，不吞异常</li>
 *     <li>close() 必须被调用</li>
 * </ul>
 */
public interface ITraceSpan extends AutoCloseable{
    /**
     * 添加字符串标签。
     *
     * <p>适合记录业务类型、渠道、状态、枚举值等。</p>
     *
     * <pre>
     * span.tag("biz.type", "order");
     * span.tag("order.channel", "douyin");
     * </pre>
     *
     * @param key 标签名
     * @param value 字符串标签值
     */
    void tag(String key, String value);

    /**
     * 添加数字标签。
     *
     * <p>适合记录数量、金额分、重试次数、批次大小等。</p>
     *
     * <pre>
     * span.tag("order.amount.cent", 19900);
     * span.tag("retry.count", 3);
     * </pre>
     *
     * @param key 标签名
     * @param value 数字标签值
     */
    void tag(String key, Number value);


    /**
     * 添加布尔标签。
     *
     * <p>适合记录是否命中缓存、是否降级、是否重试等。</p>
     *
     * <pre>
     * span.tag("cache.hit", true);
     * span.tag("fallback.used", false);
     * </pre>
     *
     * @param key 标签名
     * @param value 布尔标签值
     */
    void tag(String key, Boolean value);

    /**
     * 添加通用标签。
     *
     * <p>这是兜底方法。具体实现通常会将复杂对象转成字符串。</p>
     *
     * <p>不建议频繁使用 Object 类型，因为可能带来：</p>
     * <ul>
     *     <li>敏感字段泄露</li>
     *     <li>大对象序列化开销</li>
     *     <li>Trace 后端存储膨胀</li>
     * </ul>
     *
     * @param key 标签名
     * @param value 任意标签值
     */
    void tag(String key, Object value);

    /**
     * 记录异常。
     *
     * <p>该方法只负责把异常信息记录到当前 Span，不负责吞异常。</p>
     *
     * <p>正确模式：</p>
     * <pre>
     * catch (Throwable e) {
     *     span.error(e);
     *     throw e;
     * }
     * </pre>
     *
     * <p>不同实现可以做不同处理：</p>
     * <ul>
     *     <li>OpenTelemetry：recordException + setStatus(ERROR)</li>
     *     <li>SkyWalking：ActiveSpan.error，后续扩展</li>
     *     <li>Noop：什么都不做</li>
     * </ul>
     *
     * @param throwable 当前捕获到的异常
     */
    void error(Throwable throwable);

    /**
     * 结束当前 Span。
     *
     * <p>必须调用。否则可能导致：</p>
     * <ul>
     *     <li>Span 耗时不完整</li>
     *     <li>上下文没有恢复</li>
     *     <li>链路数据无法正确上报</li>
     * </ul>
     *
     * <p>推荐使用 try-with-resources 或在 finally 中调用。</p>
     */
    @Override
    void close();
}

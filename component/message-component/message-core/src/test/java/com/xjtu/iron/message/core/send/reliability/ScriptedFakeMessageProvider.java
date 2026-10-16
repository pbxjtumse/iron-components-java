package com.xjtu.iron.message.core.send.reliability;

import com.xjtu.iron.message.spi.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 按脚本返回发送结果的测试 Provider。
 *
 * <p>可靠发送单元测试不应该依赖真实 Kafka、Pulsar 或 RocketMQ。这个 FakeProvider 只模拟
 * {@link MessageProvider#send(ProviderSendRequest)} 的结果序列：每调用一次 send，就消费一个脚本动作。
 * 通过这种方式可以稳定制造网络失败、确认超时、明确拒绝、异常完成等场景。</p>
 */
final class ScriptedFakeMessageProvider implements MessageProvider {

    /** Provider 名称固定为 fake，方便构造 ProviderDestination。 */
    private static final String NAME = "fake";

    /** send 调用脚本。 */
    private final Queue<ScriptedSendAction> actions = new ArrayDeque<>();

    /** send 实际调用次数，用于断言是否真的发生了重试。 */
    private final AtomicInteger sendCount = new AtomicInteger();

    private ScriptedFakeMessageProvider(List<ScriptedSendAction> actions) {
        this.actions.addAll(Objects.requireNonNull(actions, "actions must not be null"));
    }

    /** 创建按顺序返回脚本动作的 FakeProvider。 */
    static ScriptedFakeMessageProvider scripted(ScriptedSendAction... actions) {
        return new ScriptedFakeMessageProvider(List.of(actions));
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<MessageCapability> capabilities() {
        return Set.of(MessageCapability.BASIC_PUBLISH);
    }

    @Override
    public CompletionStage<ProviderSendResult> send(ProviderSendRequest request) {
        sendCount.incrementAndGet();
        ScriptedSendAction action = actions.poll();
        if (action == null) {
            return CompletableFuture.completedFuture(ProviderSendResult.failed(
                    com.xjtu.iron.message.api.publish.SendStatus.FAILED,
                    com.xjtu.iron.message.api.publish.SendFailureType.CLIENT_ERROR,
                    "fake provider has no scripted result"));
        }
        return action.execute(request);
    }

    @Override
    public ProviderSubscription subscribe(ProviderSubscriptionRequest request) {
        return () -> { };
    }

    @Override
    public void close() {
        // FakeProvider 没有外部连接，不需要释放资源。
    }

    int sendCount() {
        return sendCount.get();
    }

    /**
     * 单次 send 调用的脚本动作。
     */
    interface ScriptedSendAction {

        /** 执行脚本动作并返回 Provider 层发送结果。 */
        CompletionStage<ProviderSendResult> execute(ProviderSendRequest request);

        /** 返回一个明确的 ProviderSendResult。 */
        static ScriptedSendAction returning(ProviderSendResult result) {
            return request -> CompletableFuture.completedFuture(result);
        }

        /** 返回一个已经异常完成的 CompletionStage。 */
        static ScriptedSendAction failing(Throwable throwable) {
            return request -> {
                CompletableFuture<ProviderSendResult> future = new CompletableFuture<>();
                future.completeExceptionally(throwable);
                return future;
            };
        }

        /** 模拟 Provider 实现错误：send 返回 null CompletionStage。 */
        static ScriptedSendAction nullStage() {
            return request -> null;
        }
    }
}

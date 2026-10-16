package com.xjtu.iron.message.core.send;

import com.xjtu.iron.message.api.publish.SendResult;

import java.util.concurrent.CompletionStage;
/**
 * message-core 内部的发送执行器抽象。
 *
 * <p>{@code MessageTemplate} 面向这个接口编程，而不是直接依赖某一种发送实现。
 * 当前有两种主要实现：{@code DirectMessageSender} 表示一期直发逻辑，
 * {@code DefaultReliableMessageSender} 表示二期可靠发送逻辑。</p>
 *
 * <p>这个接口让发送链路具备可插拔能力：Spring Boot 自动装配可以根据
 * {@code xjtu.iron.message.reliability.send.enabled} 决定注入哪一种发送执行器，
 * 业务代码不需要感知内部是否启用了 retry-component。</p>
 */
public interface MessageSendExecutor {

    /**
     * 同步执行一次消息发送。
     *
     * @param prepared 已准备好的发送快照
     * @return 统一发送结果
     */
    SendResult send(PreparedMessageSend prepared);

    /**
     * 异步执行一次消息发送。
     *
     * @param prepared 已准备好的发送快照
     * @return 异步发送结果
     */
    CompletionStage<SendResult> sendAsync(PreparedMessageSend prepared);
}

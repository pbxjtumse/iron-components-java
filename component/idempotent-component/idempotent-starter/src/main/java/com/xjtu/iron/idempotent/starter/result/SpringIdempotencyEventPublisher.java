package com.xjtu.iron.idempotent.starter.result;

import com.xjtu.iron.idempotent.core.observation.IdempotencyEvent;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEventPublisher;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 基于 Spring ApplicationEvent 的事件适配器。
 *
 * <p>事件属于观测旁路，因此 listener 抛出的运行时异常必须与正确性主流程隔离。
 * 不能因为某个日志/告警监听器失败，就让已经完成的 PROCESSING/SUCCESS 状态转换失败。</p>
 */
public final class SpringIdempotencyEventPublisher implements IdempotencyEventPublisher {

    private final ApplicationEventPublisher publisher;

    public SpringIdempotencyEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(IdempotencyEvent event) {
        try {
            publisher.publishEvent(event);
        } catch (RuntimeException ignored) {
            // 观测失败不得污染幂等状态机。生产环境可在这里接内部 logger。
        }
    }
}

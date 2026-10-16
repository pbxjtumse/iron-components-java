package com.xjtu.iron.distributed.lock.starter.observability;

import com.xjtu.iron.distributed.lock.core.observability.LockEvent;
import com.xjtu.iron.distributed.lock.core.observability.LockEventPublisher;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Objects;

/** Spring ApplicationEvent 事件发布器适配。 */
public final class SpringLockEventPublisher implements LockEventPublisher {
    private final ApplicationEventPublisher publisher;
    public SpringLockEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }
    @Override
    public void publish(LockEvent event) {
        publisher.publishEvent(event);
    }
}

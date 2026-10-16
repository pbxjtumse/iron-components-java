package com.xjtu.iron.retry.core.executor;

import com.xjtu.iron.foundation.time.ClockProvider;
import com.xjtu.iron.retry.api.event.RetryEvent;
import com.xjtu.iron.retry.api.event.RetryEventType;
import com.xjtu.iron.retry.api.event.RetryListener;
import com.xjtu.iron.retry.api.policy.RetryPolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 负责重试事件构造、监听器顺序通知和单个监听器故障隔离。 */
final class RetryEventDispatcher {

    private static final System.Logger LOGGER =
            System.getLogger(RetryEventDispatcher.class.getName());

    private final List<RetryListener> listeners;
    private final ClockProvider clockProvider;

    RetryEventDispatcher(List<RetryListener> listeners, ClockProvider clockProvider) {
        this.listeners = immutableListeners(listeners);
        this.clockProvider = Objects.requireNonNull(
                clockProvider,
                "clockProvider must not be null"
        );
    }

    /** 创建包含逻辑执行公共字段的事件构建器。 */
    RetryEvent.Builder newEvent(
            RetryEventType eventType,
            String retryId,
            String operationName,
            RetryPolicy retryPolicy,
            Duration elapsedTime) {
        return RetryEvent.builder(
                eventType,
                retryId,
                operationName,
                retryPolicy.getPolicyName(),
                retryPolicy.getMaxAttempts(),
                clockProvider.clock().instant()
        ).elapsedTime(elapsedTime);
    }

    /** 依次通知监听器，并隔离单个监听器的运行时异常。 */
    void publish(RetryEvent event) {
        for (RetryListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException listenerFailure) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "RetryListener failed and was isolated: listener="
                                + listener.getClass().getName()
                                + ", eventType=" + event.getEventType(),
                        listenerFailure
                );
            }
        }
    }

    private static List<RetryListener> immutableListeners(List<RetryListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return Collections.emptyList();
        }
        List<RetryListener> copiedListeners = new ArrayList<>(listeners.size());
        for (RetryListener listener : listeners) {
            copiedListeners.add(Objects.requireNonNull(
                    listener,
                    "retryListener must not be null"
            ));
        }
        return Collections.unmodifiableList(copiedListeners);
    }
}

package com.xjtu.iron.concurrency.core.listener;

import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.listener.TaskExecutionListener;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 组合任务执行监听器。
 *
 * <p>
 * 将多个 {@link TaskExecutionListener} 聚合成一个统一监听器，
 * 使任务主链路只依赖单个监听器对象。
 * </p>
 *
 * <p>
 * 该实现支持延迟解析监听器列表。Spring Starter 可以在组件启动完成后再解析
 * 业务监听器，避免某个监听器 Bean 又依赖 {@code AsyncExecutor} 时形成启动期循环依赖。
 * </p>
 *
 * <p>
 * 每个真实监听器都会收到独立的事件副本；任意监听器抛出异常时，
 * 不会影响其他监听器，也不会影响任务执行主链路。
 * </p>
 */
public final class CompositeTaskExecutionListener implements TaskExecutionListener {

    /**
     * 监听器列表提供器。
     *
     * <p>
     * 可以直接返回固定列表，也可以在第一次任务事件到达时延迟解析 Spring Bean。
     * </p>
     */
    private final Supplier<List<TaskExecutionListener>> listenerSupplier;

    /**
     * 第一次解析后缓存的真实监听器列表。
     *
     * <p>
     * Spring 单例 Bean 在应用启动完成后通常不会再动态增加，因此缓存可以避免
     * 每次任务事件都重新遍历 BeanFactory。
     * </p>
     */
    private volatile List<TaskExecutionListener> resolvedListeners;

    /**
     * 使用固定监听器列表创建组合监听器。
     *
     * @param listeners 需要组合的真实监听器列表
     */
    public CompositeTaskExecutionListener(List<TaskExecutionListener> listeners) {
        List<TaskExecutionListener> fixed = sanitize(listeners, null);
        this.listenerSupplier = () -> fixed;
        this.resolvedListeners = fixed;
    }

    /**
     * 使用延迟提供器创建组合监听器。
     *
     * <p>
     * 该构造方式主要供 Spring Starter 使用。创建 Composite Bean 时不会立即实例化
     * 全部业务监听器，从而避免业务监听器依赖异步执行入口时形成循环依赖。
     * </p>
     *
     * @param listenerSupplier 真实监听器列表提供器
     */
    public CompositeTaskExecutionListener(
            Supplier<List<TaskExecutionListener>> listenerSupplier
    ) {
        this.listenerSupplier = Objects.requireNonNull(
                listenerSupplier,
                "listenerSupplier must not be null"
        );
    }

    @Override
    public void onSubmitted(TaskExecutionEvent event) {
        invoke(event, TaskExecutionListener::onSubmitted);
    }

    @Override
    public void onStarted(TaskExecutionEvent event) {
        invoke(event, TaskExecutionListener::onStarted);
    }

    @Override
    public void onSuccess(TaskExecutionEvent event) {
        invoke(event, TaskExecutionListener::onSuccess);
    }

    @Override
    public void onFailure(TaskExecutionEvent event) {
        invoke(event, TaskExecutionListener::onFailure);
    }

    @Override
    public void onRejected(TaskExecutionEvent event) {
        invoke(event, TaskExecutionListener::onRejected);
    }

    @Override
    public void onTimeout(TaskExecutionEvent event) {
        invoke(event, TaskExecutionListener::onTimeout);
    }

    @Override
    public void onCancelled(TaskExecutionEvent event) {
        invoke(event, TaskExecutionListener::onCancelled);
    }

    @Override
    public void onFallback(TaskExecutionEvent event) {
        invoke(event, TaskExecutionListener::onFallback);
    }

    @Override
    public void onFallbackSuccess(TaskExecutionEvent event) {
        invoke(event, TaskExecutionListener::onFallbackSuccess);
    }

    @Override
    public void onFallbackFailure(TaskExecutionEvent event) {
        invoke(event, TaskExecutionListener::onFallbackFailure);
    }

    @Override
    public void onCompleted(TaskExecutionEvent event) {
        invoke(event, TaskExecutionListener::onCompleted);
    }

    /**
     * 安全调用全部真实监听器。
     *
     * @param event 原始事件
     * @param invoker 具体生命周期方法调用器
     */
    private void invoke(TaskExecutionEvent event, ListenerInvoker invoker) {
        Objects.requireNonNull(event, "event must not be null");

        for (TaskExecutionListener listener : listeners()) {
            try {
                invoker.invoke(listener, event.copy());
            } catch (Throwable ignored) {
                /*
                 * 监听器属于旁路扩展能力，不能覆盖原始任务结果。
                 * 后续可以在此接入组件内部 logger，记录监听器类型和异常摘要。
                 */
            }
        }
    }

    /**
     * 获取并缓存真实监听器列表。
     *
     * @return 已过滤空值、当前 Composite 自身和其他 Composite 的监听器列表
     */
    private List<TaskExecutionListener> listeners() {
        List<TaskExecutionListener> current = resolvedListeners;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            current = resolvedListeners;
            if (current == null) {
                current = sanitize(listenerSupplier.get(), this);
                resolvedListeners = current;
            }
            return current;
        }
    }

    /**
     * 清洗监听器列表。
     *
     * @param listeners 原始监听器列表
     * @param self 当前 Composite 实例；固定列表构造时可以为空
     * @return 不可变真实监听器列表
     */
    private static List<TaskExecutionListener> sanitize(
            List<TaskExecutionListener> listeners,
            CompositeTaskExecutionListener self
    ) {
        if (listeners == null || listeners.isEmpty()) {
            return Collections.emptyList();
        }

        return listeners.stream()
                .filter(Objects::nonNull)
                .filter(listener -> listener != self)
                .filter(listener -> !(listener instanceof CompositeTaskExecutionListener))
                .toList();
    }

    /**
     * 单个监听器生命周期方法调用器。
     */
    @FunctionalInterface
    private interface ListenerInvoker {

        /**
         * 调用指定监听器。
         *
         * @param listener 真实监听器
         * @param event 独立事件副本
         */
        void invoke(TaskExecutionListener listener, TaskExecutionEvent event);
    }
}

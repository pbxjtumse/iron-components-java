package com.xjtu.iron.relational.mybatis.observation;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/** MyBatis 技术表访问的统一观测扩展点。 */
public interface MyBatisAccessListener {

    /** Mapper 执行前回调。 */
    default void before(MyBatisAccessInvocation invocation) {
    }

    /** Mapper 成功后回调。 */
    default void afterSuccess(MyBatisAccessInvocation invocation, Duration elapsed) {
    }

    /** Mapper 失败后回调。 */
    default void afterFailure(MyBatisAccessInvocation invocation, Duration elapsed, Throwable failure) {
    }

    /** 返回无操作监听器。 */
    static MyBatisAccessListener noop() {
        return new MyBatisAccessListener() {
        };
    }

    /** 按传入顺序通知监听器，隔离每个监听器的 RuntimeException。 */
    static MyBatisAccessListener composite(List<? extends MyBatisAccessListener> listeners) {
        List<MyBatisAccessListener> snapshot = List.copyOf(listeners);
        return new MyBatisAccessListener() {
            @Override
            public void before(MyBatisAccessInvocation invocation) {
                notifyEach(snapshot, listener -> listener.before(invocation));
            }

            @Override
            public void afterSuccess(MyBatisAccessInvocation invocation, Duration elapsed) {
                notifyEach(snapshot, listener -> listener.afterSuccess(invocation, elapsed));
            }

            @Override
            public void afterFailure(MyBatisAccessInvocation invocation, Duration elapsed, Throwable failure) {
                notifyEach(snapshot, listener -> listener.afterFailure(invocation, elapsed, failure));
            }
        };
    }

    private static void notifyEach(List<MyBatisAccessListener> listeners, Consumer<MyBatisAccessListener> callback) {
        for (MyBatisAccessListener listener : listeners) {
            try {
                callback.accept(listener);
            } catch (RuntimeException ignored) {
                // 观测是旁路能力，不能改变数据库结果或阻断其他监听器。
            }
        }
    }
}

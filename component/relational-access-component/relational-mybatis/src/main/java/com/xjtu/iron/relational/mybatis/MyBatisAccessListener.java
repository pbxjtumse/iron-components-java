package com.xjtu.iron.relational.mybatis;

import java.time.Duration;

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
}

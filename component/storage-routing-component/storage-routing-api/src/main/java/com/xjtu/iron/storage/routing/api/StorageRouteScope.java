package com.xjtu.iron.storage.routing.api;

/**
 * 存储路由作用域。
 *
 * <p>StorageRouteScope 负责在执行结束后清理当前路由上下文。它继承 AutoCloseable，
 * 因此应该优先配合 try-with-resources 使用。</p>
 */
public interface StorageRouteScope extends AutoCloseable {

    /**
     * 关闭当前作用域并恢复上一层路由。
     */
    @Override
    void close();
}

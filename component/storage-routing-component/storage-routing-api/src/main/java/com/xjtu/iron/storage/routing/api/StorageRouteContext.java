package com.xjtu.iron.storage.routing.api;

import java.util.Optional;

/**
 * 当前执行上下文中的存储路由。
 *
 * <p>它用于保证一次业务执行链路中，业务 Repository、IdempotencyStorage、OutboxStorage、TaskStorage
 * 可以读取同一份路由结果，避免业务数据和技术组件记录落到不同分片。</p>
 */
public interface StorageRouteContext {

    /**
     * 返回当前路由。没有路由时返回 Optional.empty()。
     */
    Optional<StorageRoute> current();

    /**
     * 返回当前路由；没有路由时抛出异常。
     */
    default StorageRoute requireCurrent() {
        return current().orElseThrow(() -> new StorageRoutingException("No StorageRoute bound to current context"));
    }

    /**
     * 打开一个路由作用域。
     *
     * <p>推荐使用 try-with-resources，确保异常场景下也能正确清理 ThreadLocal。</p>
     */
    StorageRouteScope open(StorageRoute route);
}

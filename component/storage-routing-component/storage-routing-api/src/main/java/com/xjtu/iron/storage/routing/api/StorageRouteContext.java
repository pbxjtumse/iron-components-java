package com.xjtu.iron.storage.routing.api;

import java.util.Optional;

/**
 * 当前执行上下文中的存储路由。
 *
 * <p>这是用于保存、读取路由结果的接口；{@link RouteContext} 是一次路由的输入模型，两者职责不同。</p>
 *
 * <p>它用于保证一次业务执行链路中，业务 Repository、IdempotencyStorage、OutboxStorage、TaskStorage
 * 可以读取同一份路由结果，复用分片依据。它不自动映射各表、切换数据源或开启事务。</p>
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

package com.xjtu.iron.storage.routing.api;

/**
 * 存储路由解析器的旧包名兼容入口。
 *
 * <p>新代码请依赖 {@link com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver}。
 * 该接口继承统一契约，不再独立声明另一套方法；现有实现仍可赋值给新、旧两种接口。</p>
 *
 * @deprecated 使用 api.resolver 包下的统一入口。
 */
@Deprecated
@FunctionalInterface
public interface StorageRouteResolver extends com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver {
}

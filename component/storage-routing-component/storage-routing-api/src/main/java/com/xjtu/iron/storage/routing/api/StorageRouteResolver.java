package com.xjtu.iron.storage.routing.api;

/**
 * 存储路由解析器的包名兼容入口。
 *
 * <p>统一契约定义在 {@link com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver}。
 * 该接口只负责保留早期包名，不再声明另一套行为语义；内置实现可赋值给新、旧两种接口。
 * 自定义实现需要将方法参数迁移为 RouteContext，旧 StorageRouteRequest 仅保留调用桥接。</p>
 */
@FunctionalInterface
public interface StorageRouteResolver extends com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver {
}

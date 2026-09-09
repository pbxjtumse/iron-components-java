package com.xjtu.iron.storage.routing.api;

/**
 * 存储路由解析器。
 *
 * <p>它负责把业务侧的 shardKey / logicalTable / scene 转换成 StorageRoute。
 * 具体底层可以是直连多 DataSource、ShardingSphere-JDBC、MyCAT/Proxy，
 * 上层技术组件不应该直接感知这些实现差异。</p>
 */
@FunctionalInterface
public interface StorageRouteResolver {

    /**
     * 根据请求解析存储路由。
     *
     * @param request 路由请求
     * @return 路由结果
     */
    StorageRoute resolve(StorageRouteRequest request);
}

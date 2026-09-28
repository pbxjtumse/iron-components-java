/**
 * ShardingSphere-JDBC 接入层。
 *
 * <p>只负责把 Storage Routing 的逻辑路由交付给统一 ShardingSphere DataSource；SQL 解析、改写、
 * 真实库表路由和结果归并仍由 Apache ShardingSphere 负责。</p>
 */
package com.xjtu.iron.storage.routing.integration.shardingsphere.jdbc;

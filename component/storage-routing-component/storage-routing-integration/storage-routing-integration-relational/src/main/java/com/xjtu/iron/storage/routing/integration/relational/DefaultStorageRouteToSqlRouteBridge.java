package com.xjtu.iron.storage.routing.integration.relational;

import com.xjtu.iron.relational.api.statement.SqlRoute;
import com.xjtu.iron.storage.routing.api.exception.StorageRoutingException;
import com.xjtu.iron.storage.routing.api.route.PhysicalStorageLocation;
import com.xjtu.iron.storage.routing.api.route.storage.StorageRoute;
import com.xjtu.iron.storage.routing.api.route.StorageRouteMode;
import java.util.Objects;

/**
 * 默认直连模式桥接器。
 *
 * <p>当前只处理 DIRECT_DATASOURCE。ShardingSphere-JDBC / Proxy 场景需要专门 Adapter 决定
 * SqlRoute 是否走默认逻辑 DataSource、Hint 或代理入口，本类不提前替它们做假设。</p>

 * <p><b>流程阅读编号：R4：从存储路由转换为 SQL 输入。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. 校验 DIRECT_DATASOURCE 和物理位置，toSqlRoute 返回携带 dataSourceKey 的 SqlRoute。</li>
 *     <li>2. requireTableName 单独提供表名，由上层构造 SQL；SqlRoute 本身不承载表名替换操作。</li>
 *     <li>3. 不计算分片、不获取连接、不解析或重写 SQL；中间件模式需要专用适配实现。</li>
 * </ul>
 */
public final class DefaultStorageRouteToSqlRouteBridge implements StorageRouteToSqlRouteBridge {

    @Override
    public SqlRoute toSqlRoute(StorageRoute route) {
        // R4-1：只转换数据源选择信息；表名由 requireTableName 单独交付。
        return SqlRoute.of(requireDirectLocation(route).dataSourceKey());
    }

    @Override
    public String requireTableName(StorageRoute route) {
        return requireDirectLocation(route).tableName();
    }

    private PhysicalStorageLocation requireDirectLocation(StorageRoute route) {
        Objects.requireNonNull(route, "route must not be null");
        if (route.mode() != StorageRouteMode.DIRECT_DATASOURCE) {
            throw new StorageRoutingException("Only DIRECT_DATASOURCE route can be bridged to Relational Access: " + route.mode());
        }
        PhysicalStorageLocation location = route.location();
        if (location == null) {
            throw new StorageRoutingException("DIRECT_DATASOURCE route must contain physical location");
        }
        return location;
    }
}

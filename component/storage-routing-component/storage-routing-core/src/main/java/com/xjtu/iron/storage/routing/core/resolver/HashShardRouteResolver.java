package com.xjtu.iron.storage.routing.core.resolver;

import com.xjtu.iron.storage.routing.api.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.ShardRouteInfo;
import com.xjtu.iron.storage.routing.api.resolver.ShardRouteResolver;

/**
 * 上一版本类名的薄适配器，算法只有 HashShardResolver 中的一份实现。
 * @deprecated 使用 HashShardResolver。
 */
@Deprecated
public final class HashShardRouteResolver implements ShardRouteResolver {

    private final HashShardResolver delegate;

    public HashShardRouteResolver(int databaseCount, int tablesPerDatabase) {
        this.delegate = new HashShardResolver(databaseCount, tablesPerDatabase);
    }

    @Override
    public ShardRouteInfo resolve(CompositeShardKey shardKey) {
        return delegate.resolve(shardKey);
    }
}

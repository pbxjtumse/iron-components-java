package com.xjtu.iron.relational.mybatis;

import java.util.Objects;

/** 固定单数据源的 MyBatis Access Resolver。 */
public final class FixedMyBatisAccessResolver implements MyBatisAccessResolver {

    /** 固定物理数据源标识；默认数据源模式允许为空。 */
    private final String dataSourceKey;

    /** 唯一的 MyBatis Access。 */
    private final MyBatisAccess access;

    public FixedMyBatisAccessResolver(String dataSourceKey, MyBatisAccess access) {
        this.dataSourceKey = normalize(dataSourceKey);
        this.access = Objects.requireNonNull(access, "access must not be null");
    }

    public static FixedMyBatisAccessResolver defaultDataSource(MyBatisAccess access) {
        return new FixedMyBatisAccessResolver(null, access);
    }

    @Override
    public MyBatisAccess resolve(String requestedDataSourceKey) {
        String requested = normalize(requestedDataSourceKey);
        if (!Objects.equals(dataSourceKey, requested) && requested != null) {
            throw new IllegalArgumentException(
                    "MyBatisAccess cannot resolve dataSourceKey=" + requested
                            + "; configure RoutingMyBatisAccessResolver for multiple databases"
            );
        }
        return access;
    }

    @Override
    public boolean supportsCurrentTransactionParticipation() {
        return access.supportsCurrentTransactionParticipation();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

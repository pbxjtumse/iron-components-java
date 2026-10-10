package com.xjtu.iron.relational.mybatis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 多物理数据源的 MyBatis Access Resolver。 */
public final class RoutingMyBatisAccessResolver implements MyBatisAccessResolver {

    /** 可选默认 Access。 */
    private final MyBatisAccess defaultAccess;

    /** dataSourceKey 到 Access 的不可变映射。 */
    private final Map<String, MyBatisAccess> accesses;

    public RoutingMyBatisAccessResolver(MyBatisAccess defaultAccess, Map<String, ? extends MyBatisAccess> accesses) {
        this.defaultAccess = defaultAccess;
        Objects.requireNonNull(accesses, "accesses must not be null");
        Map<String, MyBatisAccess> copy = new LinkedHashMap<>();
        accesses.forEach((key, value) -> {
            String normalized = requireText(key, "dataSourceKey");
            MyBatisAccess previous = copy.put(normalized, Objects.requireNonNull(value, "access must not be null"));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate dataSourceKey=" + normalized);
            }
        });
        this.accesses = Map.copyOf(copy);
    }

    @Override
    public MyBatisAccess resolve(String dataSourceKey) {
        String normalized = dataSourceKey == null || dataSourceKey.isBlank() ? null : dataSourceKey.trim();
        if (normalized == null) {
            if (defaultAccess == null) {
                throw new IllegalArgumentException("no default MyBatisAccess configured");
            }
            return defaultAccess;
        }
        MyBatisAccess access = accesses.get(normalized);
        if (access == null) {
            throw new IllegalArgumentException(
                    "MyBatisAccess not found for dataSourceKey=" + normalized
                            + ", knownDataSourceKeys=" + accesses.keySet()
            );
        }
        return access;
    }

    @Override
    public boolean supportsCurrentTransactionParticipation() {
        boolean defaultSupported = defaultAccess == null || defaultAccess.supportsCurrentTransactionParticipation();
        return defaultSupported && accesses.values().stream()
                .allMatch(MyBatisAccess::supportsCurrentTransactionParticipation);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

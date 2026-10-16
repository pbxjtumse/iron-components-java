package com.xjtu.iron.message.core.routing;

import com.xjtu.iron.message.api.model.MessageDestination;

import java.util.*;
/**
 * 目的地路由注册表，保存逻辑目的地到物理目的地的静态映射。
 *
 * <p>它是 Spring Boot 配置、手工注册和运行时解析之间的桥梁。starter 会把 yml 中的 routes 转换成
 * {@code DestinationRoute} 后放入该注册表，发送和订阅时再由 {@code DestinationResolver} 查询。</p>
 *
 * <p>注册表只负责保存和查询路由，不负责选择 Provider、检查能力或执行发送。
 * 这些职责分别由 resolver、provider registry 和 sender 承担。</p>
 */
public final class DestinationRouteRegistry {

    private final Map<RouteKey, Map<String, DestinationRoute>> routes;

    public DestinationRouteRegistry(Collection<DestinationRoute> routeCollection) {
        Map<RouteKey, Map<String, DestinationRoute>> mutableRoutes = new LinkedHashMap<>();
        for (DestinationRoute route : routeCollection == null ? List.<DestinationRoute>of() : routeCollection) {
            if (route == null) {
                throw new IllegalArgumentException("destination route must not be null");
            }
            RouteKey key = new RouteKey(route.namespace(), route.name());
            Map<String, DestinationRoute> providerRoutes = mutableRoutes.computeIfAbsent(
                    key,
                    ignored -> new LinkedHashMap<>());
            DestinationRoute previous = providerRoutes.putIfAbsent(route.providerName(), route);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate destination route: " + key + ", provider=" + route.providerName());
            }
        }
        Map<RouteKey, Map<String, DestinationRoute>> immutable = new LinkedHashMap<>();
        mutableRoutes.forEach((key, value) -> immutable.put(
                key,
                Collections.unmodifiableMap(new LinkedHashMap<>(value))));
        this.routes = Collections.unmodifiableMap(immutable);
    }

    public static DestinationRouteRegistry empty() {
        return new DestinationRouteRegistry(List.of());
    }

    public Optional<DestinationRoute> find(MessageDestination destination, String providerName) {
        Map<String, DestinationRoute> providerRoutes = routes.get(RouteKey.of(destination));
        if (providerRoutes == null) {
            return Optional.empty();
        }
        String normalizedProvider = providerName == null
                ? null
                : providerName.trim().toLowerCase(Locale.ROOT);
        return Optional.ofNullable(providerRoutes.get(normalizedProvider));
    }

    public List<DestinationRoute> findAll(MessageDestination destination) {
        Map<String, DestinationRoute> providerRoutes = routes.get(RouteKey.of(destination));
        if (providerRoutes == null || providerRoutes.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(providerRoutes.values()));
    }

    private static final class RouteKey {
        /** namespace 字段。 */
        private final String namespace;

        /** name 字段。 */
        private final String name;

        /**
         * 创建不可变 RouteKey。
         */
        private RouteKey(
            String namespace,
            String name) {
            // 保存 namespace。
            this.namespace = namespace;
            // 保存 name。
            this.name = name;
        }

        private static RouteKey of(MessageDestination destination) {
            return new RouteKey(destination.namespace(), destination.name());
        }
        /**
         * 返回namespace。
         *
         * @return namespace
         */
        public String namespace() {
            // 返回不可变字段。
            return namespace;
        }

        /**
         * 返回name。
         *
         * @return name
         */
        public String name() {
            // 返回不可变字段。
            return name;
        }

        /**
         * 按全部字段比较两个值对象。
         *
         * @param object 待比较对象
         * @return 字段值全部一致时返回 true
         */
        @Override
        public boolean equals(Object object) {
            // 同一对象直接相等。
            if (this == object) {
                return true;
            }
            // 类型不同或对象为空时不相等。
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            // 转换为当前类型后逐字段比较。
            RouteKey other = (RouteKey) object;
            return Objects.equals(namespace, other.namespace)
                    && Objects.equals(name, other.name);
        }

        /**
         * 根据全部字段计算哈希值。
         *
         * @return 哈希值
         */
        @Override
        public int hashCode() {
            // 使用与 equals 相同的字段计算哈希值。
            return Objects.hash(namespace, name);
        }

        /**
         * 返回便于诊断的字段摘要。
         *
         * @return 字符串摘要
         */
        @Override
        public String toString() {
            // 拼接全部字段，保持值对象可诊断。
            return "RouteKey{" +
                    "namespace=" + namespace +
                    ", name=" + name +
                    '}';
        }

    }
}

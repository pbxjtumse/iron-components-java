package com.xjtu.iron.message.core.routing;

import com.xjtu.iron.message.api.model.MessageDestination;
import com.xjtu.iron.message.spi.ProviderDestination;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
/**
 * 默认逻辑目的地解析器，把 API 层的 {@code MessageDestination} 转换为 SPI 层的 {@code ProviderDestination}。
 *
 * <p>message-component 对业务暴露的是 namespace/name/providerHint 这类稳定逻辑目的地，
 * 具体写入哪个 Kafka Topic、Pulsar Topic 或 RocketMQ Topic，由路由表和默认 Provider 决定。
 * 这个解析动作集中在 resolver 中，避免业务代码、MessageTemplate 和 Provider 实现互相耦合。</p>
 *
 * <p>严格路由模式下，必须存在显式路由；宽松模式下，可以根据逻辑目的地推导物理目的地。
 * 后续生产级组件一般推荐严格模式，减少因配置缺失导致的误投递。</p>
 */
public final class DefaultDestinationResolver implements DestinationResolver {

    /** 精确路由注册表。 */
    private final DestinationRouteRegistry routeRegistry;

    /** 默认 Provider 名称。 */
    private final String defaultProviderName;

    /** 未配置精确路由时的处理模式。 */
    private final DestinationRoutingMode routingMode;

    /**
     * 创建默认解析器。
     *
     * @param routeRegistry 路由注册表
     * @param defaultProviderName 默认 Provider
     * @param routingMode 未配置精确路由时的处理策略
     */
    public DefaultDestinationResolver(
            DestinationRouteRegistry routeRegistry,
            String defaultProviderName,
            DestinationRoutingMode routingMode) {
        // 路由注册表不能为空。
        this.routeRegistry = Objects.requireNonNull(
                routeRegistry,
                "routeRegistry must not be null");
        // 默认 Provider 必须存在。
        if (defaultProviderName == null || defaultProviderName.isBlank()) {
            // 启动阶段拒绝非法配置。
            throw new IllegalArgumentException("defaultProviderName must not be blank");
        }
        // 保存标准化 Provider 名称。
        this.defaultProviderName = defaultProviderName.trim().toLowerCase(Locale.ROOT);
        // 保存路由模式。
        this.routingMode = Objects.requireNonNull(routingMode, "routingMode must not be null");
    }

    /**
     * 按明确优先级解析逻辑目的地。
     */
    @Override
    public ProviderDestination resolve(MessageDestination destination) {
        // 逻辑目的地不能为空。
        Objects.requireNonNull(destination, "destination must not be null");
        // providerHint 存在时优先选择对应 Provider。
        if (destination.providerHint() != null) {
            // 先查找该 Provider 的显式路由。
            return routeRegistry.find(destination, destination.providerHint())
                    // 存在显式路由时直接使用。
                    .map(DestinationRoute::toProviderDestination)
                    // 严格模式禁止 providerHint 绕过路由表。
                    .orElseGet(() -> unresolved(destination, destination.providerHint()));
        }
        // 优先查找默认 Provider 的显式路由。
        var defaultRoute = routeRegistry.find(destination, defaultProviderName);
        // 找到时直接返回。
        if (defaultRoute.isPresent()) {
            // 转换为 SPI 物理目的地。
            return defaultRoute.get().toProviderDestination();
        }
        // 获取该逻辑目的地下的全部路由。
        List<DestinationRoute> allRoutes = routeRegistry.findAll(destination);
        // 仅配置一条路由时允许直接采用，避免重复配置默认 Provider。
        if (allRoutes.size() == 1) {
            // 返回唯一配置路由。
            return allRoutes.get(0).toProviderDestination();
        }
        // 配置多条但没有默认 Provider 对应路由时属于歧义。
        if (allRoutes.size() > 1) {
            // 要求调用方修正默认 Provider 或显式 providerHint。
            throw new IllegalStateException(
                    "ambiguous destination routes for " + destination.qualifiedName()
                            + "; default provider route not found: " + defaultProviderName);
        }
        // 完全没有路由时根据路由模式决定失败或隐式生成。
        return unresolved(destination, defaultProviderName);
    }

    /**
     * 处理未配置精确路由的逻辑目的地。
     */
    private ProviderDestination unresolved(
            MessageDestination destination,
            String providerName) {
        // 严格模式要求所有生产目的地显式配置，防止拼写错误创建或误投 Topic。
        if (routingMode == DestinationRoutingMode.STRICT) {
            // 输出完整逻辑名称和目标 Provider，便于修正配置。
            throw new IllegalStateException(
                    "destination route not found: " + destination.qualifiedName()
                            + ", provider=" + providerName);
        }
        // 隐式模式使用标准化物理名称，主要供本地开发和快速验证。
        return new ProviderDestination(
                providerName,
                defaultPhysicalName(destination),
                java.util.Map.of());
    }

    /**
     * 生成没有显式配置时的默认物理名称。
     *
     * @param destination 逻辑目的地
     * @return 仅包含安全字符的默认名称
     */
    private static String defaultPhysicalName(MessageDestination destination) {
        // 拼接领域和逻辑名称；Event/Command 通过命名规范区分，不再进入一期路由身份。
        String rawName = destination.namespace() + "-" + destination.name();
        // 将常见 Provider 不一致的特殊字符统一替换为连字符。
        String sanitized = rawName.replaceAll("[^a-zA-Z0-9_-]", "-");
        // 合并连续连字符，避免生成难读名称。
        return sanitized.replaceAll("-+", "-");
    }
}

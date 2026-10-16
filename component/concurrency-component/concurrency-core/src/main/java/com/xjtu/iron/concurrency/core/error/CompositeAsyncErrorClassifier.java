package com.xjtu.iron.concurrency.core.error;

import com.xjtu.iron.concurrency.api.error.AsyncError;
import com.xjtu.iron.concurrency.api.error.AsyncErrorClassificationContext;
import com.xjtu.iron.concurrency.api.error.AsyncErrorClassificationRule;
import com.xjtu.iron.concurrency.api.error.AsyncErrorClassifier;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 组合异步错误分类器。
 *
 * <p>
 * Composite 可以理解为“把很多条小规则组合成一个统一分类器”。
 * TaskCommand 只依赖一个 AsyncErrorClassifier，但业务系统可以注册任意数量的
 * AsyncErrorClassificationRule，例如领域异常、RPC、数据库、缓存异常规则。
 * </p>
 *
 * <p>
 * 执行顺序如下：先按 order 从小到大遍历规则；第一条 supports 返回 true 的规则负责分类；
 * 如果没有任何规则匹配，则交给 fallbackClassifier 处理通用技术异常。
 * </p>
 */
public final class CompositeAsyncErrorClassifier implements AsyncErrorClassifier {

    /**
     * 按优先级排序后的业务与扩展分类规则。
     */
    private final List<AsyncErrorClassificationRule> rules;

    /**
     * 当所有扩展规则均不匹配时使用的默认技术分类器。
     */
    private final AsyncErrorClassifier fallbackClassifier;

    /**
     * 创建组合分类器。
     *
     * @param rules 业务和扩展分类规则
     * @param fallbackClassifier 默认技术分类器
     */
    public CompositeAsyncErrorClassifier(
            List<AsyncErrorClassificationRule> rules,
            AsyncErrorClassifier fallbackClassifier
    ) {
        this.rules = rules == null
                ? List.of()
                : rules.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(AsyncErrorClassificationRule::order))
                .toList();
        this.fallbackClassifier = Objects.requireNonNull(
                fallbackClassifier,
                "fallbackClassifier must not be null"
        );
    }

    @Override
    public AsyncError classify(AsyncErrorClassificationContext context) {
        Objects.requireNonNull(context, "context must not be null");

        for (AsyncErrorClassificationRule rule : rules) {
            try {
                if (!rule.supports(context)) {
                    continue;
                }

                AsyncError error = rule.classify(context);
                if (error != null) {
                    return error;
                }
            } catch (RuntimeException ruleError) {
                /*
                 * 分类规则属于旁路诊断能力，单条业务规则异常不能覆盖原始任务异常。
                 * 一期先继续尝试后续规则，后续再接入内部诊断日志/指标。
                 * 注意：不捕获 Error，避免吞掉 JVM 级严重错误。
                 */
            }
        }

        return fallbackClassifier.classify(context);
    }

    /**
     * 获取已装配的规则数量，便于诊断和测试。
     */
    public int ruleCount() {
        return rules.size();
    }
}

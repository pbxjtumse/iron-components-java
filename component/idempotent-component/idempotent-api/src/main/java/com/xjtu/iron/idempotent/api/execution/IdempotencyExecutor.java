package com.xjtu.iron.idempotent.api.execution;

import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryRequest;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicies;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicy;

import java.util.Objects;

/**
 * 幂等业务统一入口。
 *
 * <p>主 API 不要求 {@code Class<T>}。
 * 默认只保证“历史成功不重复执行”，不保存业务返回值；需要结果回放时显式传入
 * {@link IdempotencyResultPolicy}。</p>

 * <p><b>流程阅读编号：I0：业务调用契约。</b>编号按 I（幂等）、R（路由）、D（数据访问）分组，不表示所有分支均依次执行。</p>
 * <ul>
 *     <li>1. 业务构造 IdempotencyRequest 并传入 callback；接口并不要求业务直接操作 Repository。</li>
 *     <li>2. execute 是普通请求入口，recover 是显式恢复入口；实际实现可由路由装饰器包裹 Core。</li>
 *     <li>3. 返回值包含状态和错误，调用方应按状态处理，不能只判断 value 是否为空。</li>
 * </ul>
 */
public interface IdempotencyExecutor {

    /**
     * 最常用入口：不保存结果，只保证重复请求不再次执行业务。
     */
    default <T> IdempotencyResult<T> execute(IdempotencyRequest request, IdempotencyCallback<T> callback) {
        return execute(request, IdempotencyResultPolicies.none(), callback);
    }

    /**
     * 带结果策略的普通幂等执行。
     */
    <T> IdempotencyResult<T> execute(IdempotencyRequest request, IdempotencyResultPolicy<T> resultPolicy, IdempotencyCallback<T> callback);

    /**
     * 最常用恢复入口：不要求结果回放。
     */
    default <T> IdempotencyResult<T> recover(IdempotencyRecoveryRequest request, IdempotencyCallback<T> callback) {
        return recover(request, IdempotencyResultPolicies.none(), callback);
    }

    /**
     * 带结果策略的 Reliable Task 显式恢复。
     */
    <T> IdempotencyResult<T> recover(
            IdempotencyRecoveryRequest request,
            IdempotencyResultPolicy<T> resultPolicy,
            IdempotencyCallback<T> callback);

    default <T> IdempotencyResult<T> execute(String key, String policyName, IdempotencyCallback<T> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        return execute(IdempotencyRequest.of(key, policyName), callback);
    }

    default <T> IdempotencyResult<T> execute(String key, IdempotencyCallback<T> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        return execute(IdempotencyRequest.of(key), callback);
    }
}

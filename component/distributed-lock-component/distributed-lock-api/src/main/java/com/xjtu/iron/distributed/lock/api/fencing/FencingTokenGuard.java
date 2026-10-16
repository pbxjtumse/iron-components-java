package com.xjtu.iron.distributed.lock.api.fencing;

import com.xjtu.iron.distributed.lock.api.exception.FencingTokenRejectedException;
import com.xjtu.iron.distributed.lock.api.model.LockHandle;

import java.util.Objects;
import java.util.function.LongPredicate;

/**
 * 业务资源 fencing 条件写入辅助类。
 *
 * <p>该类不会替业务方生成 SQL，只统一两个关键动作：</p>
 * <ol>
 *     <li>确保当前 LockHandle 携带 fencing token；</li>
 *     <li>当业务条件更新返回 false 时，抛出 FencingTokenRejectedException，
 *     让 execute 模板映射为 FENCING_REJECTED + FENCING。</li>
 * </ol>
 */
public final class FencingTokenGuard {

    private FencingTokenGuard() {
    }

    public static long requireToken(LockHandle handle) {
        Objects.requireNonNull(handle, "handle must not be null");
        return handle.fencingToken().orElseThrow(() ->
                new IllegalStateException("fencing token is required but missing for lock: "
                        + handle.lockName()));
    }

    public static void requireAccepted(LockHandle handle, LongPredicate conditionalWrite) {
        Objects.requireNonNull(conditionalWrite, "conditionalWrite must not be null");
        long token = requireToken(handle);
        boolean accepted = conditionalWrite.test(token);
        if (!accepted) {
            throw new FencingTokenRejectedException("business resource rejected stale fencing token: lock=" + handle.lockName() + ", token=" + token);
        }
    }
}

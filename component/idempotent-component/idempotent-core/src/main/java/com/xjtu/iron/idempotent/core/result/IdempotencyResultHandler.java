package com.xjtu.iron.idempotent.core.result;

import com.xjtu.iron.idempotent.api.execution.*;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicies;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicy;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicyType;

/** 结果策略处理：保存带类型的 payload、重放历史结果；不执行业务或访问 Repository。 */
public final class IdempotencyResultHandler {
    public <T> String capture(T value, IdempotencyResultPolicy<T> resultPolicy) throws ResultCaptureException {
        if (!resultPolicy.storesPayload()) return null;
        try {
            String captured = resultPolicy.capture(value);
            if (captured == null) throw new IllegalStateException(resultPolicy.type() + " result policy returned null stored value");

            // Envelope 保存策略类型，回放时可拒绝“历史 SNAPSHOT、当前 REFERENCE”这类错误混用。
            return StoredResultEnvelope.encode(resultPolicy.type(), captured);
        } catch (Exception error) {
            throw new ResultCaptureException(error);
        }
    }

    /** SUCCESS 历史结果回放；DISCARDED 不走 replay，而由 StateMachine 返回 PREVIOUS_DISCARDED。 */
    public <T> IdempotencyResult<T> replay(IdempotencyRecord record, IdempotencyResultPolicy<T> resultPolicy, boolean lockFallback) {
        IdempotencyResultPolicy<T> resolved = resultPolicy == null ? IdempotencyResultPolicies.none() : resultPolicy;
        if (resolved.type() == IdempotencyResultPolicyType.NONE) {
            return IdempotencyResult.<T>builder().status(IdempotencyResultStatus.REPLAYED).stage(IdempotencyStage.REPLAY)
                    .record(record).lockFallback(lockFallback).build();
        }

        String payload = record == null ? null : record.getResultPayload();
        if (payload == null || payload.isBlank()) {
            return IdempotencyResult.<T>builder().status(IdempotencyResultStatus.RESULT_REPLAY_UNAVAILABLE)
                    .stage(IdempotencyStage.REPLAY).record(record)
                    .error(new IllegalStateException("historical SUCCESS has no stored result for " + resolved.type() + " replay"))
                    .lockFallback(lockFallback).build();
        }

        try {
            StoredResultEnvelope.Decoded decoded = StoredResultEnvelope.decode(payload);
            if (decoded.type() != resolved.type()) {
                return IdempotencyResult.<T>builder().status(IdempotencyResultStatus.RESULT_POLICY_MISMATCH)
                        .stage(IdempotencyStage.REPLAY).record(record)
                        .error(new IllegalStateException("stored result policy is " + decoded.type()
                                + " but current request uses " + resolved.type()))
                        .lockFallback(lockFallback).build();
            }
            return IdempotencyResult.<T>builder().status(IdempotencyResultStatus.REPLAYED).stage(IdempotencyStage.REPLAY)
                    .value(resolved.replay(decoded.value())).record(record).lockFallback(lockFallback).build();
        } catch (Exception error) {
            return IdempotencyResult.<T>builder().status(IdempotencyResultStatus.RESULT_POLICY_ERROR).stage(IdempotencyStage.REPLAY)
                    .record(record).error(error).lockFallback(lockFallback).build();
        }
    }

}

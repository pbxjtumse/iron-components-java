package com.xjtu.iron.idempotent.core.execution.business;

import com.xjtu.iron.idempotent.api.execution.*;
import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.repository.write.*;
import com.xjtu.iron.idempotent.api.spi.IdempotencyFailureClassifier;
import com.xjtu.iron.idempotent.core.execution.preparation.IdempotencyExecutionDefinition;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEvent;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEventPublisher;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEventType;
import com.xjtu.iron.idempotent.core.observation.IdempotencyMetrics;
import com.xjtu.iron.idempotent.core.result.IdempotencyResultHandler;
import com.xjtu.iron.idempotent.core.result.ResultCaptureException;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionException;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionOutcome;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 获得执行权后的业务编排：创建本次执行信息，选择事务方式，处理完成或失败。 */
public final class IdempotencyBusinessExecutor {
    private final IdempotencyFailureClassifier failureClassifier;
    private final IdempotencyTransactionCoordinator transactionCoordinator;
    private final IdempotencyResultHandler resultHandler;
    private final IdempotencyEventPublisher events;
    private final IdempotencyMetrics metrics;
    private final Clock clock;

    public IdempotencyBusinessExecutor(IdempotencyFailureClassifier failureClassifier, IdempotencyTransactionCoordinator transactionCoordinator,
                                      IdempotencyResultHandler resultHandler, IdempotencyEventPublisher events, IdempotencyMetrics metrics, Clock clock) {
        this.failureClassifier = failureClassifier;
        this.transactionCoordinator = transactionCoordinator;
        this.resultHandler = resultHandler;
        this.events = events;
        this.metrics = metrics;
        this.clock = clock;
    }

    public <T> IdempotencyResult<T> executeAcquired(String key, String routeKey, IdempotencyExecutionDefinition<T> definition,
                                                   IdempotencyRecord record, IdempotencyCallback<T> callback,
                                                   Instant startedAt, boolean lockFallback, boolean recoveryExecution) {
        if (record == null) return missingAcquiredRecord(lockFallback);
        publish(IdempotencyEventType.EXECUTION_STARTED, IdempotencyStage.EXECUTE, definition.policy(), definition.repository(), null);
        BusinessExecution<T> execution = createExecution(key, routeKey, definition, record, startedAt, lockFallback, recoveryExecution);
        return execution.transactionApplied() ? executeInTransaction(execution, callback) : executeWithoutTransaction(execution, callback);
    }

    private <T> BusinessExecution<T> createExecution(String key, String routeKey, IdempotencyExecutionDefinition<T> definition,
                                                    IdempotencyRecord record, Instant startedAt, boolean lockFallback, boolean recoveryExecution) {
        IdempotencyPolicy policy = definition.policy();
        IdempotencyContext context = new IdempotencyContext(record.storageContext(), policy.getNamespace(), key, normalize(routeKey),
                record.getOwnerToken(), record.getVersion(), policy.getMode(), recoveryExecution,
                record.getUpdatedAt() == null ? Instant.now(clock) : record.getUpdatedAt(), record.getProcessingExpireAt());
        boolean transactionApplied = transactionCoordinator != null && definition.repository().capabilities().isBusinessTransactionParticipationSupported();
        return new BusinessExecution<>(key, routeKey, definition, record, context, startedAt, lockFallback, recoveryExecution, transactionApplied);
    }

    private <T> IdempotencyResult<T> executeInTransaction(BusinessExecution<T> execution, IdempotencyCallback<T> callback) {
        try {
            BusinessCompletion<T> completion = transactionCoordinator.executeRequired(
                    transactionName(execution.policy()), normalize(execution.routeKey()), () -> executeTransactionWork(execution, callback));
            return completeSuccess(execution, completion);
        } catch (CompletionRejectedException rejected) {
            return handleCompletionRejected(execution, rejected.write);
        } catch (ResultCaptureException error) {
            return handleResultCaptureFailure(execution, error);
        } catch (IdempotencyTransactionException error) {
            return handleTransactionFailure(execution, error);
        } catch (Throwable error) {
            return handleBusinessFailure(execution, error);
        }
    }

    /** 此方法必须在 Tx-B 的 callback 内执行；完成被拒绝时抛异常，不能把失败结果正常返回给事务协调器。 */
    private <T> BusinessCompletion<T> executeTransactionWork(BusinessExecution<T> execution, IdempotencyCallback<T> callback) throws Exception {
        BusinessCompletion<T> completion = executeBusinessAndWriteSuccess(execution, callback);
        if (completion.write().getStatus() != IdempotencyWriteStatus.UPDATED) throw new CompletionRejectedException(completion.write());
        return completion;
    }

    private <T> IdempotencyResult<T> executeWithoutTransaction(BusinessExecution<T> execution, IdempotencyCallback<T> callback) {
        try {
            // 无事务参与时，业务写入与 SUCCESS 更新之间仍存在进程崩溃窗口。
            BusinessCompletion<T> completion = executeBusinessAndWriteSuccess(execution, callback);
            return completion.write().getStatus() == IdempotencyWriteStatus.UPDATED
                    ? completeSuccess(execution, completion) : handleCompletionRejected(execution, completion.write());
        } catch (ResultCaptureException error) {
            return handleResultCaptureFailure(execution, error);
        } catch (Throwable error) {
            return handleBusinessFailure(execution, error);
        }
    }

    /** 两种执行方式共享业务顺序；事务的开启和关闭由调用方负责。 */
    private <T> BusinessCompletion<T> executeBusinessAndWriteSuccess(BusinessExecution<T> execution, IdempotencyCallback<T> callback) throws Exception {
        T value = callback.doWithIdempotency(execution.context());
        String payload = resultHandler.capture(value, execution.definition().resultPolicy());
        IdempotencyWriteResult write = execution.repository().markSuccess(createSuccessRequest(execution, payload));
        return new BusinessCompletion<>(value, write);
    }

    private <T> IdempotencyResult<T> completeSuccess(BusinessExecution<T> execution, BusinessCompletion<T> completion) {
        publish(execution, IdempotencyEventType.EXECUTION_SUCCESS, IdempotencyStage.COMPLETE_STATE, null);
        IdempotencyResultStatus status = execution.recoveryExecution() ? IdempotencyResultStatus.RECOVERED : IdempotencyResultStatus.EXECUTED;
        return finish(execution, IdempotencyResult.<T>builder().status(status).stage(IdempotencyStage.COMPLETE_STATE)
                .value(completion.value()).record(completion.write().getRecord()));
    }

    private <T> IdempotencyResult<T> handleCompletionRejected(BusinessExecution<T> execution, IdempotencyWriteResult write) {
        boolean ownershipLost = write.getStatus() == IdempotencyWriteStatus.STALE_OWNER || write.getStatus() == IdempotencyWriteStatus.ALREADY_FINAL;
        if (ownershipLost) publish(execution, IdempotencyEventType.OWNERSHIP_LOST, IdempotencyStage.COMPLETE_STATE, null);
        IdempotencyResultStatus status = ownershipLost ? IdempotencyResultStatus.OWNERSHIP_LOST : IdempotencyResultStatus.REPOSITORY_ERROR;
        return finish(execution, IdempotencyResult.<T>builder().status(status).stage(IdempotencyStage.COMPLETE_STATE)
                .record(write.getRecord()).error(write.getError()));
    }

    private <T> IdempotencyResult<T> handleResultCaptureFailure(BusinessExecution<T> execution, ResultCaptureException error) {
        IdempotencyFailureInfo failure = new IdempotencyFailureInfo("RESULT_POLICY_ERROR", safeMessage(error.getCause()), false, Instant.now(clock));
        IdempotencyWriteResult write = persistFailure(execution, failure);
        // 保持既有异常语义；本轮仅整理流程，不改变无事务分支的错误附加行为。
        if (execution.transactionApplied()) attachProviderFailure(error, write);
        return finish(execution, IdempotencyResult.<T>builder().status(IdempotencyResultStatus.RESULT_POLICY_ERROR).stage(IdempotencyStage.COMPLETE_STATE)
                .record(recordAfterFailure(execution, write)).error(error.getCause()));
    }

    private <T> IdempotencyResult<T> handleBusinessFailure(BusinessExecution<T> execution, Throwable error) {
        if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        IdempotencyWriteResult write = persistFailure(execution, failureClassifier.classify(error, Instant.now(clock)));
        attachProviderFailure(error, write);
        publish(execution, IdempotencyEventType.EXECUTION_FAILED, IdempotencyStage.EXECUTE, error);
        return finish(execution, IdempotencyResult.<T>builder().status(IdempotencyResultStatus.EXECUTION_FAILED).stage(IdempotencyStage.EXECUTE)
                .record(recordAfterFailure(execution, write)).error(error));
    }

    private <T> IdempotencyResult<T> handleTransactionFailure(BusinessExecution<T> execution, IdempotencyTransactionException error) {
        if (error.outcome() == IdempotencyTransactionOutcome.COMMIT_UNKNOWN) {
            publish(execution, IdempotencyEventType.TRANSACTION_COMMIT_UNKNOWN, IdempotencyStage.TRANSACTION, error);
            return finish(execution, IdempotencyResult.<T>builder().status(IdempotencyResultStatus.TRANSACTION_COMMIT_UNKNOWN).stage(IdempotencyStage.TRANSACTION)
                    .record(execution.record()).error(error));
        }
        // 已确认 Tx-B 失败/回滚，才由 Repository 的独立状态事务记录 FAILED。
        IdempotencyFailureInfo failure = new IdempotencyFailureInfo("TRANSACTION_" + error.outcome().name(), safeMessage(error), true, Instant.now(clock));
        IdempotencyWriteResult write = persistFailure(execution, failure);
        attachProviderFailure(error, write);
        publish(execution, IdempotencyEventType.TRANSACTION_FAILED, IdempotencyStage.TRANSACTION, error);
        return finish(execution, IdempotencyResult.<T>builder().status(IdempotencyResultStatus.TRANSACTION_FAILED).stage(IdempotencyStage.TRANSACTION)
                .record(recordAfterFailure(execution, write)).error(error));
    }

    private IdempotencySuccessRequest createSuccessRequest(BusinessExecution<?> execution, String payload) {
        IdempotencyPolicy policy = execution.policy();
        IdempotencyRecord record = execution.record();
        return new IdempotencySuccessRequest(record.storageContext(), policy.getNamespace(), execution.key(), record.getOwnerToken(), record.getVersion(),
                payload, policy.getMode(), policy.getIdempotencyWindow(), policy.getWindowPolicy(), policy.getRecordRetentionTtl(), Instant.now(clock));
    }

    private IdempotencyWriteResult persistFailure(BusinessExecution<?> execution, IdempotencyFailureInfo failure) {
        return execution.repository().markFailed(createFailureRequest(execution, failure));
    }

    private IdempotencyFailureRequest createFailureRequest(BusinessExecution<?> execution, IdempotencyFailureInfo failure) {
        IdempotencyPolicy policy = execution.policy();
        IdempotencyRecord record = execution.record();
        return new IdempotencyFailureRequest(record.storageContext(), policy.getNamespace(), execution.key(), record.getOwnerToken(), record.getVersion(),
                failure, policy.getMode(), policy.getIdempotencyWindow(), policy.getWindowPolicy(), policy.getRecordRetentionTtl(), failure.getOccurredAt());
    }

    private IdempotencyRecord recordAfterFailure(BusinessExecution<?> execution, IdempotencyWriteResult write) {
        return write.getRecord() == null ? execution.record() : write.getRecord();
    }

    /** 调用方决定结果内容；此处统一补充执行标记并记录指标。Builder 仅用于本次调用。 */
    private <T> IdempotencyResult<T> finish(BusinessExecution<T> execution, IdempotencyResult.Builder<T> resultBuilder) {
        IdempotencyResult<T> result = resultBuilder.lockFallback(execution.lockFallback()).transactionApplied(execution.transactionApplied()).build();
        metrics.recordExecution(execution.policy().getMode(), execution.repository().providerName(), result.getStatus(),
                Duration.between(execution.startedAt(), Instant.now(clock)));
        return result;
    }

    private <T> IdempotencyResult<T> missingAcquiredRecord(boolean lockFallback) {
        return IdempotencyResult.<T>builder().status(IdempotencyResultStatus.REPOSITORY_ERROR).stage(IdempotencyStage.ACQUIRE_STATE)
                .error(new IllegalStateException("ACQUIRED result has no record")).lockFallback(lockFallback).build();
    }

    private void publish(BusinessExecution<?> execution, IdempotencyEventType type, IdempotencyStage stage, Throwable error) {
        publish(type, stage, execution.policy(), execution.repository(), error);
    }

    private void publish(IdempotencyEventType type, IdempotencyStage stage, IdempotencyPolicy policy, IdempotencyRepository repository, Throwable error) {
        events.publish(new IdempotencyEvent(type, stage, policy.getMode(), repository.providerName(), Instant.now(clock), error));
    }

    private void attachProviderFailure(Throwable primary, IdempotencyWriteResult write) {
        if (write != null && write.getStatus() == IdempotencyWriteStatus.PROVIDER_ERROR && write.getError() != null && write.getError() != primary) {
            primary.addSuppressed(write.getError());
        }
    }

    private String safeMessage(Throwable error) {
        if (error == null) return null;
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private String transactionName(IdempotencyPolicy policy) { return "idempotency-business:" + policy.getNamespace(); }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private record BusinessCompletion<T>(T value, IdempotencyWriteResult write) { }

    private static final class CompletionRejectedException extends RuntimeException {
        private final IdempotencyWriteResult write;
        private CompletionRejectedException(IdempotencyWriteResult write) {
            super("idempotency completion rejected: " + (write == null ? "null" : write.getStatus()));
            this.write = Objects.requireNonNull(write, "write must not be null");
        }
    }
}

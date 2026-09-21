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

/** 获得执行权后的业务执行与完成处理；业务回调、结果捕获和 SUCCESS 条件更新保持在同一事务工作中。 */
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
        if (record == null) {
            return simple(IdempotencyResultStatus.REPOSITORY_ERROR, IdempotencyStage.ACQUIRE_STATE, null,
                    new IllegalStateException("ACQUIRED result has no record"), lockFallback);
        }

        IdempotencyPolicy policy = definition.policy();
        IdempotencyRepository repository = definition.repository();
        publish(IdempotencyEventType.EXECUTION_STARTED, IdempotencyStage.EXECUTE, policy, repository, null);

        // Context 对业务可见，暴露的是当前 generation 身份；业务若传递恢复任务，应保留这些字段。
        IdempotencyContext context = new IdempotencyContext(
                record.storageContext(), policy.getNamespace(), key, normalize(routeKey), record.getOwnerToken(), record.getVersion(),
                policy.getMode(), recoveryExecution, record.getUpdatedAt() == null ? Instant.now(clock) : record.getUpdatedAt(),
                record.getProcessingExpireAt());

        // 只有 transactionCoordinator 存在且 Repository 能参与当前事务，才可把业务写入和 SUCCESS 写入放进同一 Tx-B。
        boolean transactionApplied = transactionCoordinator != null
                && repository.capabilities().isBusinessTransactionParticipationSupported();
        if (transactionApplied) {
            return executeOwnedTransactionally(key, routeKey, definition, record, callback, context,
                    startedAt, lockFallback, recoveryExecution);
        }
        return executeOwnedWithoutBusinessTransaction(key, definition, record, callback, context,
                startedAt, lockFallback, recoveryExecution);
    }

    private <T> IdempotencyResult<T> executeOwnedTransactionally(String key, String routeKey,
                                                                  IdempotencyExecutionDefinition<T> definition,
                                                                  IdempotencyRecord record, IdempotencyCallback<T> callback,
                                                                  IdempotencyContext context, Instant startedAt,
                                                                  boolean lockFallback, boolean recoveryExecution) {
        IdempotencyPolicy policy = definition.policy();
        IdempotencyRepository repository = definition.repository();
        try {
            TransactionalCompletion<T> completion = transactionCoordinator.executeRequired(
                    transactionName(policy), normalize(routeKey), () -> {
                        // Tx-B 内先执行业务，再捕获返回值，最后 owner/version 条件更新 SUCCESS。
                        T value = callback.doWithIdempotency(context);
                        String resultPayload = resultHandler.capture(value, definition.resultPolicy());
                        IdempotencyWriteResult write = repository.markSuccess(
                                successRequest(key, policy, record, resultPayload, Instant.now(clock)));
                        if (write.getStatus() != IdempotencyWriteStatus.UPDATED) {
                            // stale owner 或终态冲突必须抛出，让 Tx-B 回滚业务写入，避免旧 owner 泄漏提交。
                            throw new CompletionRejectedException(write);
                        }
                        return new TransactionalCompletion<>(value, write.getRecord());
                    });

            publish(IdempotencyEventType.EXECUTION_SUCCESS, IdempotencyStage.COMPLETE_STATE, policy, repository, null);
            return finish(recoveryExecution ? IdempotencyResultStatus.RECOVERED : IdempotencyResultStatus.EXECUTED,
                    IdempotencyStage.COMPLETE_STATE, completion.value, completion.record, null, policy, repository,
                    startedAt, lockFallback, true);

        } catch (CompletionRejectedException rejected) {
            IdempotencyWriteResult write = rejected.write;
            if (write.getStatus() == IdempotencyWriteStatus.STALE_OWNER || write.getStatus() == IdempotencyWriteStatus.ALREADY_FINAL) {
                publish(IdempotencyEventType.OWNERSHIP_LOST, IdempotencyStage.COMPLETE_STATE, policy, repository, null);
                return finish(IdempotencyResultStatus.OWNERSHIP_LOST, IdempotencyStage.COMPLETE_STATE, null, write.getRecord(),
                        write.getError(), policy, repository, startedAt, lockFallback, true);
            }
            return finish(IdempotencyResultStatus.REPOSITORY_ERROR, IdempotencyStage.COMPLETE_STATE, null, write.getRecord(),
                    write.getError(), policy, repository, startedAt, lockFallback, true);

        } catch (ResultCaptureException resultError) {
            IdempotencyWriteResult failureWrite = persistFailure(key, policy, repository, record,
                    new IdempotencyFailureInfo("RESULT_POLICY_ERROR", safeMessage(resultError.getCause()), false, Instant.now(clock)));
            attachProviderFailure(resultError, failureWrite);
            return finish(IdempotencyResultStatus.RESULT_POLICY_ERROR, IdempotencyStage.COMPLETE_STATE, null,
                    failureWrite.getRecord() == null ? record : failureWrite.getRecord(), resultError.getCause(),
                    policy, repository, startedAt, lockFallback, true);

        } catch (IdempotencyTransactionException transactionError) {
            return handleTransactionFailure(key, policy, repository, record, transactionError, startedAt, lockFallback);

        } catch (Throwable businessError) {
            return handleBusinessFailure(key, policy, repository, record, businessError, startedAt, lockFallback, true);
        }
    }

    private <T> IdempotencyResult<T> executeOwnedWithoutBusinessTransaction(String key,
                                                                             IdempotencyExecutionDefinition<T> definition,
                                                                             IdempotencyRecord record,
                                                                             IdempotencyCallback<T> callback,
                                                                             IdempotencyContext context,
                                                                             Instant startedAt,
                                                                             boolean lockFallback,
                                                                             boolean recoveryExecution) {
        IdempotencyPolicy policy = definition.policy();
        IdempotencyRepository repository = definition.repository();
        try {
            // 无事务参与时，业务写入与 markSuccess 之间可能存在进程崩溃窗口；适用于能接受最终恢复的场景。
            T value = callback.doWithIdempotency(context);
            String resultPayload = resultHandler.capture(value, definition.resultPolicy());
            IdempotencyWriteResult write = repository.markSuccess(successRequest(key, policy, record, resultPayload, Instant.now(clock)));
            if (write.getStatus() == IdempotencyWriteStatus.UPDATED) {
                publish(IdempotencyEventType.EXECUTION_SUCCESS, IdempotencyStage.COMPLETE_STATE, policy, repository, null);
                return finish(recoveryExecution ? IdempotencyResultStatus.RECOVERED : IdempotencyResultStatus.EXECUTED,
                        IdempotencyStage.COMPLETE_STATE, value, write.getRecord(), null, policy, repository,
                        startedAt, lockFallback, false);
            }
            if (write.getStatus() == IdempotencyWriteStatus.STALE_OWNER || write.getStatus() == IdempotencyWriteStatus.ALREADY_FINAL) {
                publish(IdempotencyEventType.OWNERSHIP_LOST, IdempotencyStage.COMPLETE_STATE, policy, repository, null);
                return finish(IdempotencyResultStatus.OWNERSHIP_LOST, IdempotencyStage.COMPLETE_STATE, null,
                        write.getRecord(), write.getError(), policy, repository, startedAt, lockFallback, false);
            }
            return finish(IdempotencyResultStatus.REPOSITORY_ERROR, IdempotencyStage.COMPLETE_STATE, null,
                    write.getRecord(), write.getError(), policy, repository, startedAt, lockFallback, false);

        } catch (ResultCaptureException resultError) {
            IdempotencyWriteResult failureWrite = persistFailure(key, policy, repository, record,
                    new IdempotencyFailureInfo("RESULT_POLICY_ERROR", safeMessage(resultError.getCause()), false, Instant.now(clock)));
            return finish(IdempotencyResultStatus.RESULT_POLICY_ERROR, IdempotencyStage.COMPLETE_STATE, null,
                    failureWrite.getRecord() == null ? record : failureWrite.getRecord(), resultError.getCause(),
                    policy, repository, startedAt, lockFallback, false);
        } catch (Throwable businessError) {
            return handleBusinessFailure(key, policy, repository, record, businessError, startedAt, lockFallback, false);
        }
    }

    private IdempotencySuccessRequest successRequest(String key, IdempotencyPolicy policy, IdempotencyRecord record,
                                                     String resultPayload, Instant now) {
        return new IdempotencySuccessRequest(record.storageContext(), policy.getNamespace(), key, record.getOwnerToken(), record.getVersion(),
                resultPayload, policy.getMode(), policy.getIdempotencyWindow(), policy.getWindowPolicy(), policy.getRecordRetentionTtl(), now);
    }

    private <T> IdempotencyResult<T> handleBusinessFailure(String key, IdempotencyPolicy policy, IdempotencyRepository repository,
                                                            IdempotencyRecord record, Throwable businessError, Instant startedAt,
                                                            boolean lockFallback, boolean transactionApplied) {
        if (businessError instanceof InterruptedException) Thread.currentThread().interrupt();
        IdempotencyFailureInfo failure = failureClassifier.classify(businessError, Instant.now(clock));

        // 失败写入使用当前 owner/version，若 Recovery 已接管，旧 owner 的 FAILED 也会被 Repository 拒绝。
        IdempotencyWriteResult write = persistFailure(key, policy, repository, record, failure);
        attachProviderFailure(businessError, write);
        publish(IdempotencyEventType.EXECUTION_FAILED, IdempotencyStage.EXECUTE, policy, repository, businessError);
        return finish(IdempotencyResultStatus.EXECUTION_FAILED, IdempotencyStage.EXECUTE, null,
                write.getRecord() == null ? record : write.getRecord(), businessError, policy, repository,
                startedAt, lockFallback, transactionApplied);
    }

    private <T> IdempotencyResult<T> handleTransactionFailure(String key, IdempotencyPolicy policy, IdempotencyRepository repository,
                                                               IdempotencyRecord record, IdempotencyTransactionException error,
                                                               Instant startedAt, boolean lockFallback) {
        if (error.outcome() == IdempotencyTransactionOutcome.COMMIT_UNKNOWN) {
            publish(IdempotencyEventType.TRANSACTION_COMMIT_UNKNOWN, IdempotencyStage.TRANSACTION, policy, repository, error);
            return finish(IdempotencyResultStatus.TRANSACTION_COMMIT_UNKNOWN, IdempotencyStage.TRANSACTION, null, record, error,
                    policy, repository, startedAt, lockFallback, true);
        }

        // 已确认 Tx-B 失败/回滚后，Tx-C 用独立事务记录 FAILED，便于后续观测或显式恢复。
        IdempotencyWriteResult write = persistFailure(key, policy, repository, record,
                new IdempotencyFailureInfo("TRANSACTION_" + error.outcome().name(), safeMessage(error), true, Instant.now(clock)));
        attachProviderFailure(error, write);
        publish(IdempotencyEventType.TRANSACTION_FAILED, IdempotencyStage.TRANSACTION, policy, repository, error);
        return finish(IdempotencyResultStatus.TRANSACTION_FAILED, IdempotencyStage.TRANSACTION, null,
                write.getRecord() == null ? record : write.getRecord(), error, policy, repository, startedAt, lockFallback, true);
    }

    private IdempotencyWriteResult persistFailure(String key, IdempotencyPolicy policy, IdempotencyRepository repository,
                                                   IdempotencyRecord record, IdempotencyFailureInfo failure) {
        return repository.markFailed(new IdempotencyFailureRequest(
                record.storageContext(), policy.getNamespace(), key, record.getOwnerToken(), record.getVersion(), failure,
                policy.getMode(), policy.getIdempotencyWindow(), policy.getWindowPolicy(), policy.getRecordRetentionTtl(),
                failure.getOccurredAt()));
    }

    private void attachProviderFailure(Throwable primary, IdempotencyWriteResult write) {
        if (write != null && write.getStatus() == IdempotencyWriteStatus.PROVIDER_ERROR
                && write.getError() != null && write.getError() != primary) {
            primary.addSuppressed(write.getError());
        }
    }

    private String transactionName(IdempotencyPolicy policy) { return "idempotency-business:" + policy.getNamespace(); }

    private String safeMessage(Throwable error) {
        if (error == null) return null;
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private <T> IdempotencyResult<T> simple(IdempotencyResultStatus status, IdempotencyStage stage,
                                             IdempotencyRecord record, Throwable error, boolean lockFallback) {
        return IdempotencyResult.<T>builder().status(status).stage(stage).record(record).error(error)
                .lockFallback(lockFallback).build();
    }

    private <T> IdempotencyResult<T> finish(IdempotencyResultStatus status, IdempotencyStage stage, T value,
                                             IdempotencyRecord record, Throwable error, IdempotencyPolicy policy,
                                             IdempotencyRepository repository, Instant startedAt,
                                             boolean lockFallback, boolean transactionApplied) {
        metrics.recordExecution(policy.getMode(), repository.providerName(), status, Duration.between(startedAt, Instant.now(clock)));
        return IdempotencyResult.<T>builder().status(status).stage(stage).value(value).record(record).error(error)
                .lockFallback(lockFallback).transactionApplied(transactionApplied).build();
    }

    private void publish(IdempotencyEventType type, IdempotencyStage stage, IdempotencyPolicy policy,
                         IdempotencyRepository repository, Throwable error) {
        events.publish(new IdempotencyEvent(type, stage, policy.getMode(), repository.providerName(), Instant.now(clock), error));
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static final class CompletionRejectedException extends RuntimeException {
        private final IdempotencyWriteResult write;

        private CompletionRejectedException(IdempotencyWriteResult write) {
            super("idempotency completion rejected: " + (write == null ? "null" : write.getStatus()));
            this.write = Objects.requireNonNull(write, "write must not be null");
        }
    }

    private static final class TransactionalCompletion<T> {
        private final T value;
        private final IdempotencyRecord record;

        private TransactionalCompletion(T value, IdempotencyRecord record) {
            this.value = value;
            this.record = record;
        }
    }

}

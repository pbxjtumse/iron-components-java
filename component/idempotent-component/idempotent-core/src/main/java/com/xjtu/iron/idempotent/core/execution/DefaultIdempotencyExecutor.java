package com.xjtu.iron.idempotent.core.execution;

import com.xjtu.iron.distributed.lock.api.LockWaitStrategy;
import com.xjtu.iron.distributed.lock.api.client.DistributedLockClient;
import com.xjtu.iron.distributed.lock.api.client.LockCallback;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.idempotent.api.execution.*;
import com.xjtu.iron.idempotent.api.policy.IdempotencyLockOptions;
import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryRequest;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireResult;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireStatus;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryResult;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryStatus;
import com.xjtu.iron.idempotent.api.repository.write.*;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicies;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicy;
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicyType;
import com.xjtu.iron.idempotent.api.spi.IdempotencyFailureClassifier;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEvent;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEventPublisher;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEventType;
import com.xjtu.iron.idempotent.core.observation.IdempotencyMetrics;
import com.xjtu.iron.idempotent.core.owner.IdempotencyOwnerTokenGenerator;
import com.xjtu.iron.idempotent.core.policy.IdempotencyPolicyRegistry;
import com.xjtu.iron.idempotent.core.repository.IdempotencyRepositoryRegistry;
import com.xjtu.iron.idempotent.core.result.StoredResultEnvelope;
import com.xjtu.iron.idempotent.core.state.DefaultIdempotencyStateMachine;
import com.xjtu.iron.idempotent.core.state.IdempotencyStateAction;
import com.xjtu.iron.idempotent.core.state.IdempotencyStateDecision;
import com.xjtu.iron.idempotent.core.state.IdempotencyStateMachine;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionCoordinator;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionException;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionOutcome;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 默认幂等执行器。
 *
 * <p>V2 主链仍保持：Request -> Policy -> optional short Lock -> Repository atomic state -> StateMachine -> Business -> final CAS。
 * 新增的 storeName/shardKey/scanBucket 只扩展“记录落在哪里/如何扫描”，不会改变 generation 正确性模型。</p>
 */
public final class DefaultIdempotencyExecutor implements IdempotencyExecutor {

    /** Repository 路由表，按 mode 与 repositoryName 找到真正的 JDBC/Redis Provider。 */
    private final IdempotencyRepositoryRegistry repositoryRegistry;

    /** Policy 路由表，负责把 policyName / inline policy / default policy 解析成稳定策略。 */
    private final IdempotencyPolicyRegistry policyRegistry;

    /** 每次获得执行权时生成新的 ownerToken，和 version 一起组成 generation 身份。 */
    private final IdempotencyOwnerTokenGenerator ownerGenerator;

    /** 将业务异常归类成可恢复/不可恢复失败，供 markFailed 持久化。 */
    private final IdempotencyFailureClassifier failureClassifier;

    /** 可选短分布式锁客户端；只包裹 Repository 状态抢占，不包裹完整业务执行。 */
    private final DistributedLockClient lockClient;

    /** 可选事务协调器；存在且 Repository 支持时，才能形成 Business + SUCCESS 的 Tx-B 闭环。 */
    private final IdempotencyTransactionCoordinator transactionCoordinator;

    /** 纯状态机：把 Repository 原子返回翻译成 EXECUTE / REPLAY / RETURN。 */
    private final IdempotencyStateMachine stateMachine;

    /** 幂等生命周期事件出口，默认 noop，不影响主链正确性。 */
    private final IdempotencyEventPublisher events;

    /** 指标出口，默认 noop，只记录状态和耗时，不参与幂等判断。 */
    private final IdempotencyMetrics metrics;

    /** 统一时间源，便于测试中固定 now，也避免各层自己取系统时间。 */
    private final Clock clock;

    public DefaultIdempotencyExecutor(IdempotencyRepositoryRegistry repositoryRegistry, IdempotencyPolicyRegistry policyRegistry,
                                      IdempotencyOwnerTokenGenerator ownerGenerator, IdempotencyFailureClassifier failureClassifier,
                                      DistributedLockClient lockClient, IdempotencyTransactionCoordinator transactionCoordinator,
                                      IdempotencyStateMachine stateMachine, IdempotencyEventPublisher events,
                                      IdempotencyMetrics metrics, Clock clock) {
        this.repositoryRegistry = Objects.requireNonNull(repositoryRegistry, "repositoryRegistry must not be null");
        this.policyRegistry = Objects.requireNonNull(policyRegistry, "policyRegistry must not be null");
        this.ownerGenerator = Objects.requireNonNull(ownerGenerator, "ownerGenerator must not be null");
        this.failureClassifier = Objects.requireNonNull(failureClassifier, "failureClassifier must not be null");
        this.lockClient = lockClient;
        this.transactionCoordinator = transactionCoordinator;
        this.stateMachine = stateMachine == null ? new DefaultIdempotencyStateMachine() : stateMachine;
        this.events = events == null ? IdempotencyEventPublisher.noop() : events;
        this.metrics = metrics == null ? IdempotencyMetrics.noop() : metrics;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public <T> IdempotencyResult<T> execute(IdempotencyRequest request, IdempotencyResultPolicy<T> resultPolicy,
                                             IdempotencyCallback<T> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        Instant startedAt = Instant.now(clock);

        IdempotencyExecutionDefinition<T> definition;
        try {
            validateNormalRequest(request);
            definition = prepare(request.getPolicyName(), request.getPolicy(), resultPolicy);
        } catch (RuntimeException error) {
            return invalid(error);
        }

        IdempotencyPolicy policy = definition.policy();
        IdempotencyRepository repository = definition.repository();

        // ownerToken 在一次 execute 尝试内固定；真正能否成为当前 generation 由 Repository.tryAcquire 原子决定。
        String ownerToken = ownerGenerator.generate(policy.getNamespace(), request.getKey());
        IdempotencyStorageContext storage = request.storageContext();

        // Repository 入参携带完整策略快照，Provider 不再反向依赖 Starter 配置。
        IdempotencyAcquireRequest acquireRequest = new IdempotencyAcquireRequest(
                storage, policy.getNamespace(), request.getKey(), normalize(request.getRequestHash()), normalize(request.getRouteKey()),
                ownerToken, policy.getMode(), policy.getProcessingTimeout(), policy.getIdempotencyWindow(), policy.getWindowPolicy(),
                policy.getRecordRetentionTtl(), policy.getRecoveryPolicy().getMode(), Instant.now(clock));

        publish(IdempotencyEventType.ACQUIRE_ATTEMPT, IdempotencyStage.ACQUIRE_STATE, policy, repository, null);
        StateInvocation<IdempotencyAcquireResult> invocation = invokeWithOptionalLock(
                policy, repository, storage, request.getRouteKey(), request.getKey(), () -> repository.tryAcquire(acquireRequest));
        if (invocation.lockRejected) {
            return lockRejected(invocation.error);
        }

        IdempotencyAcquireResult acquire = invocation.result;
        metrics.recordAcquire(policy.getMode(), repository.providerName(), acquire.getStatus().name());
        IdempotencyStateDecision decision = stateMachine.onAcquire(acquire.getStatus());
        return applyAcquireDecision(decision, request, definition, acquire, callback, startedAt, invocation.lockFallback);
    }

    @Override
    public <T> IdempotencyResult<T> recover(IdempotencyRecoveryRequest request, IdempotencyResultPolicy<T> resultPolicy,
                                             IdempotencyCallback<T> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        Instant startedAt = Instant.now(clock);

        IdempotencyExecutionDefinition<T> definition;
        try {
            validateRecoveryRequest(request);
            definition = prepare(request.getPolicyName(), request.getPolicy(), resultPolicy);
        } catch (RuntimeException error) {
            return invalid(error);
        }

        IdempotencyPolicy policy = definition.policy();
        IdempotencyRepository repository = definition.repository();
        IdempotencyRecoveryPolicy recoveryPolicy = policy.getRecoveryPolicy();

        // recover() 是显式可靠任务入口；Policy 未允许时，不能把普通超时请求自动升级为恢复执行。
        if (!recoveryPolicy.isExternalTaskEnabled()) {
            return simple(IdempotencyResultStatus.RECOVERY_NOT_ALLOWED, IdempotencyStage.RECOVER_STATE, null,
                    new IllegalStateException("recovery policy does not enable EXTERNAL_TASK"), false);
        }

        // Recovery 会产生新的 owner；expectedOwner/expectedVersion 用来确认扫描 candidate 没有过期。
        String newOwner = ownerGenerator.generate(policy.getNamespace(), request.getKey());
        IdempotencyStorageContext storage = request.storageContext();
        IdempotencyRecoveryAcquireRequest recoveryRequest = new IdempotencyRecoveryAcquireRequest(
                storage, policy.getNamespace(), request.getKey(), normalize(request.getRequestHash()), normalize(request.getRouteKey()),
                newOwner, normalize(request.getExpectedOwnerToken()), request.getExpectedVersion(), policy.getMode(),
                policy.getProcessingTimeout(), recoveryPolicy.isRecoverProcessingTimeout(),
                recoveryPolicy.isRecoverRetryableFailure(), Instant.now(clock));

        publish(IdempotencyEventType.RECOVERY_ATTEMPT, IdempotencyStage.RECOVER_STATE, policy, repository, null);
        StateInvocation<IdempotencyRecoveryResult> invocation = invokeWithOptionalLock(
                policy, repository, storage, request.getRouteKey(), request.getKey(), () -> repository.tryRecover(recoveryRequest));
        if (invocation.lockRejected) {
            return lockRejected(invocation.error);
        }

        IdempotencyRecoveryResult recovery = invocation.result;
        IdempotencyStateDecision decision = stateMachine.onRecovery(recovery.getStatus());
        if (decision.action() == IdempotencyStateAction.EXECUTE) {
            return executeOwned(request.getKey(), request.getRouteKey(), definition, recovery.getRecord(), callback,
                    startedAt, invocation.lockFallback, true);
        }
        if (decision.action() == IdempotencyStateAction.REPLAY) {
            return replay(recovery.getRecord(), definition.resultPolicy(), invocation.lockFallback);
        }
        Throwable error = recovery.getStatus() == IdempotencyRecoveryStatus.PROVIDER_ERROR ? recovery.getError() : null;
        return simple(decision.resultStatus(), IdempotencyStage.RECOVER_STATE, recovery.getRecord(), error, invocation.lockFallback);
    }

    private <T> IdempotencyResult<T> applyAcquireDecision(IdempotencyStateDecision decision, IdempotencyRequest request,
                                                           IdempotencyExecutionDefinition<T> definition,
                                                           IdempotencyAcquireResult acquire, IdempotencyCallback<T> callback,
                                                           Instant startedAt, boolean lockFallback) {
        if (decision.action() == IdempotencyStateAction.EXECUTE) {
            return executeOwned(request.getKey(), request.getRouteKey(), definition, acquire.getRecord(), callback,
                    startedAt, lockFallback, false);
        }
        if (decision.action() == IdempotencyStateAction.REPLAY) {
            return replay(acquire.getRecord(), definition.resultPolicy(), lockFallback);
        }
        Throwable error = acquire.getStatus() == IdempotencyAcquireStatus.PROVIDER_ERROR ? acquire.getError() : null;
        return simple(decision.resultStatus(), IdempotencyStage.ACQUIRE_STATE, acquire.getRecord(), error, lockFallback);
    }

    private <T> IdempotencyExecutionDefinition<T> prepare(String policyName, IdempotencyPolicy inlinePolicy,
                                                           IdempotencyResultPolicy<T> resultPolicy) {
        IdempotencyPolicy policy = policyRegistry.resolve(policyName, inlinePolicy);
        policy.validate();
        IdempotencyRepository repository = repositoryRegistry.resolve(policy.getMode(), policy.getRepositoryName());
        IdempotencyResultPolicy<T> resolved = resultPolicy == null ? IdempotencyResultPolicies.none() : resultPolicy;

        // ResultPolicy 若需要持久化返回值，Provider 必须明确支持 result_payload，不能靠调用方假设。
        if (resolved.storesPayload() && !repository.capabilities().isResultPayloadSupported()) {
            throw new IllegalArgumentException("repository " + repository.providerName() + " does not support result payload storage");
        }
        return new IdempotencyExecutionDefinition<>(policy, repository, resolved);
    }

    private void validateNormalRequest(IdempotencyRequest request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        validateKey(request.getKey());
        request.storageContext();
    }

    private void validateRecoveryRequest(IdempotencyRecoveryRequest request) {
        if (request == null) throw new IllegalArgumentException("recovery request must not be null");
        validateKey(request.getKey());
        request.storageContext();
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("idempotency key must not be blank");
    }

    private <T> IdempotencyResult<T> executeOwned(String key, String routeKey, IdempotencyExecutionDefinition<T> definition,
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
                        String resultPayload = captureResult(value, definition.resultPolicy());
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

        } catch (ResultPolicyException resultError) {
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
            String resultPayload = captureResult(value, definition.resultPolicy());
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

        } catch (ResultPolicyException resultError) {
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

    private <T> String captureResult(T value, IdempotencyResultPolicy<T> resultPolicy) throws ResultPolicyException {
        if (!resultPolicy.storesPayload()) return null;
        try {
            String captured = resultPolicy.capture(value);
            if (captured == null) throw new IllegalStateException(resultPolicy.type() + " result policy returned null stored value");

            // Envelope 保存策略类型，回放时可拒绝“历史 SNAPSHOT、当前 REFERENCE”这类错误混用。
            return StoredResultEnvelope.encode(resultPolicy.type(), captured);
        } catch (Exception error) {
            throw new ResultPolicyException(error);
        }
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

    /** 可选锁只包 tryAcquire/tryRecover，且 lock identity 必须包含 storeName。 */
    private <R> StateInvocation<R> invokeWithOptionalLock(IdempotencyPolicy policy, IdempotencyRepository repository,
                                                           IdempotencyStorageContext storage, String routeKey, String key,
                                                           StateOperation<R> operation) {
        IdempotencyLockOptions lock = policy.getLockOptions();
        if (!lock.isEnabled()) return StateInvocation.direct(operation.invoke());

        if (lockClient == null) {
            if (lock.isFallbackToStateOnFailure()) {
                publish(IdempotencyEventType.LOCK_FALLBACK, IdempotencyStage.LOCK, policy, repository, null);
                return StateInvocation.fallback(operation.invoke());
            }
            return StateInvocation.lockRejected(new IllegalStateException("lock enabled but DistributedLockClient unavailable"));
        }

        LockWaitStrategy waitStrategy = lock.getWaitTime().isZero() ? LockWaitStrategy.NO_WAIT : LockWaitStrategy.BACKOFF;
        LockOptions lockOptions = LockOptions.builder()
                .namespace("idempotency:" + policy.getNamespace())
                .providerName(lock.getProviderName())
                .waitTime(lock.getWaitTime())
                .waitStrategy(waitStrategy)
                .leaseTime(lock.getLeaseTime())
                .autoRenew(false)
                .fencingRequired(false)
                .build();

        String lockName = "state:" + storage.getStoreName() + ":" + routePart(routeKey) + ":" + key;
        LockResult<R> result = lockClient.execute(lockName, lockOptions, (LockCallback<R>) handle -> operation.invoke());
        if (result.isSuccess() && result.value().isPresent()) return StateInvocation.direct(result.value().get());
        if (lock.isFallbackToStateOnFailure()) {
            publish(IdempotencyEventType.LOCK_FALLBACK, IdempotencyStage.LOCK, policy, repository, result.error().orElse(null));
            return StateInvocation.fallback(operation.invoke());
        }
        return StateInvocation.lockRejected(result.error().orElseGet(
                () -> new IllegalStateException("distributed lock not acquired: " + result.status())));
    }

    private String routePart(String routeKey) {
        String normalized = normalize(routeKey);
        return normalized == null ? "_" : normalized;
    }

    /** SUCCESS 历史结果回放；DISCARDED 不走 replay，而由 StateMachine 返回 PREVIOUS_DISCARDED。 */
    private <T> IdempotencyResult<T> replay(IdempotencyRecord record, IdempotencyResultPolicy<T> resultPolicy, boolean lockFallback) {
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

    private <T> IdempotencyResult<T> invalid(Throwable error) {
        return IdempotencyResult.<T>builder().status(IdempotencyResultStatus.VALIDATION_FAILED)
                .stage(IdempotencyStage.VALIDATE).error(error).build();
    }

    private <T> IdempotencyResult<T> lockRejected(Throwable error) {
        return IdempotencyResult.<T>builder().status(IdempotencyResultStatus.LOCK_NOT_ACQUIRED)
                .stage(IdempotencyStage.LOCK).error(error).build();
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

    @FunctionalInterface
    private interface StateOperation<R> { R invoke(); }

    private static final class StateInvocation<R> {
        private final R result;
        private final boolean lockFallback;
        private final boolean lockRejected;
        private final Throwable error;

        private StateInvocation(R result, boolean lockFallback, boolean lockRejected, Throwable error) {
            this.result = result;
            this.lockFallback = lockFallback;
            this.lockRejected = lockRejected;
            this.error = error;
        }

        private static <R> StateInvocation<R> direct(R result) { return new StateInvocation<>(result, false, false, null); }
        private static <R> StateInvocation<R> fallback(R result) { return new StateInvocation<>(result, true, false, null); }
        private static <R> StateInvocation<R> lockRejected(Throwable error) { return new StateInvocation<>(null, false, true, error); }
    }

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

    private static final class ResultPolicyException extends Exception {
        private ResultPolicyException(Throwable cause) { super(cause); }
    }
}

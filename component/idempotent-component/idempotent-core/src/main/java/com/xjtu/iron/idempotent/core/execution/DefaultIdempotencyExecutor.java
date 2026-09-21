package com.xjtu.iron.idempotent.core.execution;

import com.xjtu.iron.distributed.lock.api.client.DistributedLockClient;
import com.xjtu.iron.idempotent.api.execution.*;
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
import com.xjtu.iron.idempotent.api.result.IdempotencyResultPolicy;
import com.xjtu.iron.idempotent.api.spi.IdempotencyFailureClassifier;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.core.execution.business.IdempotencyBusinessExecutor;
import com.xjtu.iron.idempotent.core.execution.lock.IdempotencyStateOperationExecutor;
import com.xjtu.iron.idempotent.core.execution.lock.StateOperationOutcome;
import com.xjtu.iron.idempotent.core.execution.preparation.IdempotencyExecutionDefinition;
import com.xjtu.iron.idempotent.core.execution.preparation.IdempotencyExecutionPreparer;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEvent;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEventPublisher;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEventType;
import com.xjtu.iron.idempotent.core.observation.IdempotencyMetrics;
import com.xjtu.iron.idempotent.core.owner.IdempotencyOwnerTokenGenerator;
import com.xjtu.iron.idempotent.core.policy.IdempotencyPolicyRegistry;
import com.xjtu.iron.idempotent.core.repository.IdempotencyRepositoryRegistry;
import com.xjtu.iron.idempotent.core.result.IdempotencyResultHandler;
import com.xjtu.iron.idempotent.core.state.DefaultIdempotencyStateMachine;
import com.xjtu.iron.idempotent.core.state.IdempotencyStateAction;
import com.xjtu.iron.idempotent.core.state.IdempotencyStateDecision;
import com.xjtu.iron.idempotent.core.state.IdempotencyStateMachine;
import com.xjtu.iron.idempotent.core.transaction.IdempotencyTransactionCoordinator;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** 幂等流程入口：准备 -> 原子抢占 -> 状态决策 -> 业务执行或结果重放。物理路由由外层 integration 绑定。 */
public final class DefaultIdempotencyExecutor implements IdempotencyExecutor {
    private final IdempotencyExecutionPreparer preparer;
    private final IdempotencyStateOperationExecutor stateOperations;
    private final IdempotencyBusinessExecutor businessExecutor;
    private final IdempotencyResultHandler resultHandler;
    private final IdempotencyOwnerTokenGenerator ownerGenerator;
    private final IdempotencyStateMachine stateMachine;
    private final IdempotencyEventPublisher events;
    private final IdempotencyMetrics metrics;
    private final Clock clock;

    /** 保持原有装配入口；内部按准备、状态调用、业务执行和结果策略分工。 */
    public DefaultIdempotencyExecutor(IdempotencyRepositoryRegistry repositoryRegistry, IdempotencyPolicyRegistry policyRegistry,
                                      IdempotencyOwnerTokenGenerator ownerGenerator, IdempotencyFailureClassifier failureClassifier,
                                      DistributedLockClient lockClient, IdempotencyTransactionCoordinator transactionCoordinator,
                                      IdempotencyStateMachine stateMachine, IdempotencyEventPublisher events,
                                      IdempotencyMetrics metrics, Clock clock) {
        this.preparer = new IdempotencyExecutionPreparer(repositoryRegistry, policyRegistry);
        this.ownerGenerator = Objects.requireNonNull(ownerGenerator, "ownerGenerator must not be null");
        Objects.requireNonNull(failureClassifier, "failureClassifier must not be null");
        this.stateMachine = stateMachine == null ? new DefaultIdempotencyStateMachine() : stateMachine;
        this.events = events == null ? IdempotencyEventPublisher.noop() : events;
        this.metrics = metrics == null ? IdempotencyMetrics.noop() : metrics;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.resultHandler = new IdempotencyResultHandler();
        this.stateOperations = new IdempotencyStateOperationExecutor(lockClient, this.events, this.clock);
        this.businessExecutor = new IdempotencyBusinessExecutor(failureClassifier, transactionCoordinator, resultHandler, this.events, this.metrics, this.clock);
    }

    @Override
    public <T> IdempotencyResult<T> execute(IdempotencyRequest request, IdempotencyResultPolicy<T> resultPolicy,
                                             IdempotencyCallback<T> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        Instant startedAt = Instant.now(clock);

        IdempotencyExecutionDefinition<T> definition;
        try {
            definition = preparer.prepareExecution(request, resultPolicy);
        } catch (RuntimeException error) {
            return invalid(error);
        }

        IdempotencyPolicy policy = definition.policy();
        IdempotencyRepository repository = definition.repository();

        // ownerToken 在一次 execute 尝试内固定；真正能否成为当前 generation 由 Repository.tryAcquire 原子决定。
        String ownerToken = ownerGenerator.generate(policy.getNamespace(), request.getKey());
        IdempotencyStorageContext storage = request.storageContext();

        // Repository 入参携带完整策略快照，Provider 不再反向依赖 Starter 配置。
        IdempotencyAcquireRequest acquireRequest = IdempotencyAcquireRequest.builder()
                .storageContext(storage).namespace(policy.getNamespace()).key(request.getKey())
                .requestHash(normalize(request.getRequestHash())).routeKey(normalize(request.getRouteKey())).ownerToken(ownerToken)
                .mode(policy.getMode()).processingTimeout(policy.getProcessingTimeout()).idempotencyWindow(policy.getIdempotencyWindow())
                .windowPolicy(policy.getWindowPolicy()).recordRetentionTtl(policy.getRecordRetentionTtl())
                .recoveryMode(policy.getRecoveryPolicy().getMode()).now(Instant.now(clock)).build();

        publish(IdempotencyEventType.ACQUIRE_ATTEMPT, IdempotencyStage.ACQUIRE_STATE, policy, repository, null);
        StateOperationOutcome<IdempotencyAcquireResult> invocation = stateOperations.invoke(
                policy, repository, storage, request.getRouteKey(), request.getKey(), () -> repository.tryAcquire(acquireRequest));
        if (invocation.lockRejected()) {
            return lockRejected(invocation.error());
        }

        IdempotencyAcquireResult acquire = invocation.result();
        metrics.recordAcquire(policy.getMode(), repository.providerName(), acquire.getStatus().name());
        IdempotencyStateDecision decision = stateMachine.onAcquire(acquire.getStatus());
        return applyAcquireDecision(decision, request, definition, acquire, callback, startedAt, invocation.lockFallback());
    }

    @Override
    public <T> IdempotencyResult<T> recover(IdempotencyRecoveryRequest request, IdempotencyResultPolicy<T> resultPolicy,
                                             IdempotencyCallback<T> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        Instant startedAt = Instant.now(clock);

        IdempotencyExecutionDefinition<T> definition;
        try {
            definition = preparer.prepareRecovery(request, resultPolicy);
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
        StateOperationOutcome<IdempotencyRecoveryResult> invocation = stateOperations.invoke(
                policy, repository, storage, request.getRouteKey(), request.getKey(), () -> repository.tryRecover(recoveryRequest));
        if (invocation.lockRejected()) {
            return lockRejected(invocation.error());
        }

        IdempotencyRecoveryResult recovery = invocation.result();
        IdempotencyStateDecision decision = stateMachine.onRecovery(recovery.getStatus());
        if (decision.action() == IdempotencyStateAction.EXECUTE) {
            return businessExecutor.executeAcquired(request.getKey(), request.getRouteKey(), definition, recovery.getRecord(), callback,
                    startedAt, invocation.lockFallback(), true);
        }
        if (decision.action() == IdempotencyStateAction.REPLAY) {
            return resultHandler.replay(recovery.getRecord(), definition.resultPolicy(), invocation.lockFallback());
        }
        Throwable error = recovery.getStatus() == IdempotencyRecoveryStatus.PROVIDER_ERROR ? recovery.getError() : null;
        return simple(decision.resultStatus(), IdempotencyStage.RECOVER_STATE, recovery.getRecord(), error, invocation.lockFallback());
    }

    private <T> IdempotencyResult<T> applyAcquireDecision(IdempotencyStateDecision decision, IdempotencyRequest request,
                                                           IdempotencyExecutionDefinition<T> definition,
                                                           IdempotencyAcquireResult acquire, IdempotencyCallback<T> callback,
                                                           Instant startedAt, boolean lockFallback) {
        if (decision.action() == IdempotencyStateAction.EXECUTE) {
            return businessExecutor.executeAcquired(request.getKey(), request.getRouteKey(), definition, acquire.getRecord(), callback,
                    startedAt, lockFallback, false);
        }
        if (decision.action() == IdempotencyStateAction.REPLAY) {
            return resultHandler.replay(acquire.getRecord(), definition.resultPolicy(), lockFallback);
        }
        Throwable error = acquire.getStatus() == IdempotencyAcquireStatus.PROVIDER_ERROR ? acquire.getError() : null;
        return simple(decision.resultStatus(), IdempotencyStage.ACQUIRE_STATE, acquire.getRecord(), error, lockFallback);
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

    private void publish(IdempotencyEventType type, IdempotencyStage stage, IdempotencyPolicy policy,
                         IdempotencyRepository repository, Throwable error) {
        events.publish(new IdempotencyEvent(type, stage, policy.getMode(), repository.providerName(), Instant.now(clock), error));
    }

    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }

}

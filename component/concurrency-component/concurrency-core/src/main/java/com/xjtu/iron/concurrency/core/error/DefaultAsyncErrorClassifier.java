package com.xjtu.iron.concurrency.core.error;

import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorCategory;
import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorReason;
import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorStage;
import com.xjtu.iron.concurrency.api.enums.error.AsyncRecoveryAction;
import com.xjtu.iron.concurrency.api.error.*;
import com.xjtu.iron.concurrency.api.exception.ConcurrencyException;
import com.xjtu.iron.concurrency.api.exception.ConcurrencyRejectedException;
import com.xjtu.iron.concurrency.api.exception.ThreadPoolNotFoundException;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * 默认异步错误分类器。
 *
 * <p>
 * 默认实现只识别并行组件异常和 JDK 常见异常。
 * 领域异常、应用异常、RPC 和数据库异常应通过 AsyncErrorClassificationRule 扩展。
 * </p>
 */
public final class DefaultAsyncErrorClassifier implements AsyncErrorClassifier {

    @Override
    public AsyncError classify(AsyncErrorClassificationContext context) {
        Objects.requireNonNull(context, "context must not be null");

        Throwable root = context.getRootCause();
        Throwable throwable = context.getThrowable();
        AsyncErrorStage actualStage = context.getStage() == null
                ? AsyncErrorStage.NONE
                : context.getStage();

        if (root == null) {
            return AsyncError.none();
        }

        if (root instanceof ConcurrencyRejectedException
                || root instanceof RejectedExecutionException) {
            return error(
                    AsyncErrorCategory.RESOURCE,
                    AsyncErrorReason.REJECTED,
                    actualStage == AsyncErrorStage.NONE ? AsyncErrorStage.SUBMIT : actualStage,
                    throwable,
                    RecoveryHint.of(AsyncRecoveryAction.DELAY_RETRY, true, false, true)
            );
        }

        if (root instanceof TimeoutException) {
            boolean queueTimeout = actualStage == AsyncErrorStage.QUEUE;
            return error(
                    queueTimeout ? AsyncErrorCategory.RESOURCE : AsyncErrorCategory.DEPENDENCY,
                    queueTimeout ? AsyncErrorReason.QUEUE_TIMEOUT : AsyncErrorReason.TIMEOUT,
                    queueTimeout ? AsyncErrorStage.QUEUE : AsyncErrorStage.WAIT_RESULT,
                    throwable,
                    RecoveryHint.of(
                            queueTimeout ? AsyncRecoveryAction.DELAY_RETRY : AsyncRecoveryAction.FAST_RETRY,
                            true,
                            false,
                            queueTimeout
                    )
            );
        }

        if (root instanceof CancellationException) {
            return error(
                    AsyncErrorCategory.COMPONENT,
                    AsyncErrorReason.CANCELLED,
                    AsyncErrorStage.CANCEL,
                    throwable,
                    RecoveryHint.none()
            );
        }

        if (root instanceof ThreadPoolNotFoundException) {
            return error(
                    AsyncErrorCategory.COMPONENT,
                    AsyncErrorReason.EXECUTOR_NOT_FOUND,
                    AsyncErrorStage.SUBMIT,
                    throwable,
                    RecoveryHint.of(AsyncRecoveryAction.ALERT, false, false, true)
            );
        }

        if (root instanceof ConcurrencyException) {
            return error(
                    AsyncErrorCategory.COMPONENT,
                    AsyncErrorReason.TASK_THROWN,
                    actualStage == AsyncErrorStage.NONE ? AsyncErrorStage.RUN : actualStage,
                    throwable,
                    RecoveryHint.of(AsyncRecoveryAction.ALERT, false, false, true)
            );
        }

        if (root instanceof NullPointerException
                || root instanceof ClassCastException
                || root instanceof IllegalStateException
                || root instanceof IllegalArgumentException) {
            return error(
                    AsyncErrorCategory.SYSTEM,
                    AsyncErrorReason.TASK_THROWN,
                    actualStage == AsyncErrorStage.NONE ? AsyncErrorStage.RUN : actualStage,
                    throwable,
                    RecoveryHint.of(AsyncRecoveryAction.ALERT, false, false, true)
            );
        }

        return error(
                AsyncErrorCategory.UNKNOWN,
                AsyncErrorReason.UNKNOWN,
                actualStage,
                throwable,
                RecoveryHint.of(AsyncRecoveryAction.ALERT, false, false, true)
        );
    }

    /**
     * 创建没有应用业务信息的通用技术错误。
     */
    private AsyncError error(
            AsyncErrorCategory category,
            AsyncErrorReason reason,
            AsyncErrorStage stage,
            Throwable throwable,
            RecoveryHint recoveryHint
    ) {
        return AsyncError.builder()
                .classification(AsyncErrorClassification.of(category, reason, stage))
                .application(ApplicationErrorInfo.none())
                .exception(ExceptionInfo.from(throwable))
                .recovery(recoveryHint)
                .build();
    }
}

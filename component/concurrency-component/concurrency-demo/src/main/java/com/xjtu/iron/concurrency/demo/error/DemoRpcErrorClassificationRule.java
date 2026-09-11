package com.xjtu.iron.concurrency.demo.error;

import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorCategory;
import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorReason;
import com.xjtu.iron.concurrency.api.enums.error.AsyncRecoveryAction;
import com.xjtu.iron.concurrency.api.error.*;
import org.springframework.stereotype.Component;

/**
 * Demo RPC 依赖异常分类规则。
 */
@Component
public final class DemoRpcErrorClassificationRule
        implements AsyncErrorClassificationRule {

    @Override
    public boolean supports(AsyncErrorClassificationContext context) {
        return context.getRootCause() instanceof DemoRpcException;
    }

    @Override
    public AsyncError classify(AsyncErrorClassificationContext context) {
        DemoRpcException exception =
                (DemoRpcException) context.getRootCause();

        return AsyncError.builder()
                .classification(AsyncErrorClassification.of(
                        AsyncErrorCategory.DEPENDENCY,
                        AsyncErrorReason.TASK_THROWN,
                        context.getStage()
                ))
                .exception(ExceptionInfo.from(context.getThrowable()))
                .recovery(RecoveryHint.of(
                        AsyncRecoveryAction.FAST_RETRY,
                        true,
                        false,
                        false
                ))
                .attribute("remoteService", exception.getRemoteService())
                .build();
    }

    @Override
    public int order() {
        return -50;
    }
}

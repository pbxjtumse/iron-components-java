package com.xjtu.iron.concurrency.demo.error;

import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorCategory;
import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorReason;
import com.xjtu.iron.concurrency.api.enums.error.AsyncRecoveryAction;
import com.xjtu.iron.concurrency.api.error.*;
import org.springframework.stereotype.Component;

/**
 * Demo 领域异常分类规则。
 *
 * <p>
 * 该规则展示业务系统如何把自己的 DomainException 映射为 concurrency-api 定义的 AsyncError。
 * </p>
 */
@Component
public final class DemoDomainErrorClassificationRule
        implements AsyncErrorClassificationRule {

    @Override
    public boolean supports(AsyncErrorClassificationContext context) {
        return context.getRootCause() instanceof DemoDomainException;
    }

    @Override
    public AsyncError classify(AsyncErrorClassificationContext context) {
        DemoDomainException exception =
                (DemoDomainException) context.getRootCause();

        return AsyncError.builder()
                .classification(AsyncErrorClassification.of(
                        AsyncErrorCategory.APPLICATION,
                        AsyncErrorReason.TASK_THROWN,
                        context.getStage()
                ))
                .application(ApplicationErrorInfo.of(
                        exception.getErrorCode(),
                        exception.getSceneCode(),
                        exception.getMessage()
                ))
                .exception(ExceptionInfo.from(context.getThrowable()))
                .recovery(RecoveryHint.of(
                        AsyncRecoveryAction.COMPENSATE,
                        false,
                        true,
                        true
                ))
                .build();
    }

    @Override
    public int order() {
        return -100;
    }
}

package com.xjtu.iron.concurrency.core.testfixture;

import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorCategory;
import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorReason;
import com.xjtu.iron.concurrency.api.error.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingAsyncErrorClassifier
        implements AsyncErrorClassifier {

    private final List<AsyncErrorClassificationContext> contexts =
            new CopyOnWriteArrayList<>();

    private volatile RuntimeException throwableOnClassify;

    @Override
    public AsyncError classify(AsyncErrorClassificationContext context) {
        contexts.add(context);

        if (throwableOnClassify != null) {
            throw throwableOnClassify;
        }

        return AsyncError.builder()
                .classification(AsyncErrorClassification.of(
                        AsyncErrorCategory.UNKNOWN,
                        AsyncErrorReason.UNKNOWN,
                        context.getStage()
                ))
                .exception(ExceptionInfo.from(context.getThrowable()))
                .build();
    }

    public List<AsyncErrorClassificationContext> contexts() {
        return new ArrayList<>(contexts);
    }

    public AsyncErrorClassificationContext lastContext() {
        List<AsyncErrorClassificationContext> copy = contexts();
        if (copy.isEmpty()) {
            throw new AssertionError("No classify context recorded");
        }
        return copy.get(copy.size() - 1);
    }

    public void throwOnClassify(RuntimeException exception) {
        this.throwableOnClassify = exception;
    }
}

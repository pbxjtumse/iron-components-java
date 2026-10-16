package com.xjtu.iron.concurrency.api.error;

import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorCategory;
import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorReason;
import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorStage;

/**
 * 异步错误分类信息。
 *
 * <p>
 * 用于描述错误的大类、具体原因和发生阶段。
 * </p>
 */
public class AsyncErrorClassification {

    /**
     * 错误大类。
     */
    private AsyncErrorCategory category = AsyncErrorCategory.NONE;

    /**
     * 错误具体原因。
     */
    private AsyncErrorReason reason = AsyncErrorReason.NONE;

    /**
     * 错误发生阶段。
     */
    private AsyncErrorStage stage = AsyncErrorStage.NONE;

    public static AsyncErrorClassification none() {
        return of(AsyncErrorCategory.NONE, AsyncErrorReason.NONE, AsyncErrorStage.NONE);
    }

    public static AsyncErrorClassification of(
            AsyncErrorCategory category,
            AsyncErrorReason reason,
            AsyncErrorStage stage
    ) {
        AsyncErrorClassification classification = new AsyncErrorClassification();
        classification.setCategory(category);
        classification.setReason(reason);
        classification.setStage(stage);
        return classification;
    }

    public AsyncErrorClassification copy() {
        return of(category, reason, stage);
    }

    public AsyncErrorCategory getCategory() {
        return category;
    }

    public void setCategory(AsyncErrorCategory category) {
        this.category = category == null ? AsyncErrorCategory.UNKNOWN : category;
    }

    public AsyncErrorReason getReason() {
        return reason;
    }

    public void setReason(AsyncErrorReason reason) {
        this.reason = reason == null ? AsyncErrorReason.UNKNOWN : reason;
    }

    public AsyncErrorStage getStage() {
        return stage;
    }

    public void setStage(AsyncErrorStage stage) {
        this.stage = stage == null ? AsyncErrorStage.NONE : stage;
    }
}

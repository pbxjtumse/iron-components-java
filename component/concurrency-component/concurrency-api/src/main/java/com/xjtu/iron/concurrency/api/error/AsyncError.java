package com.xjtu.iron.concurrency.api.error;

import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorCategory;
import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorReason;
import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorStage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异步任务错误描述。
 * 业务系统把自己的 DomainException / BizException 映射成 AsyncError。
 * <p>
 * 用于描述一次异步任务非正常结束的完整错误信息。
 * 它由错误分类、应用侧错误信息、Java 异常信息、恢复建议和扩展字段组成。
 * </p>
 */
public class AsyncError {

    /**
     * 错误分类信息。
     */
    private AsyncErrorClassification classification = AsyncErrorClassification.none();

    /**
     * 应用侧错误信息。
     */
    private ApplicationErrorInfo application = ApplicationErrorInfo.none();

    /**
     * Java 异常信息。
     */
    private ExceptionInfo exception = ExceptionInfo.none();

    /**
     * 错误恢复建议。
     */
    private RecoveryHint recovery = RecoveryHint.none();

    /**
     * 扩展字段。
     *
     * <p>
     * 用于存放少量额外诊断信息，例如 remoteService、httpStatus、requestId。
     * 注意不要放敏感信息，也不要放高基数字段作为指标标签。
     * </p>
     */
    private Map<String, String> attributes = new LinkedHashMap<>();

    public static AsyncError none() {
        return AsyncError.builder()
                .classification(AsyncErrorClassification.none())
                .application(ApplicationErrorInfo.none())
                .exception(ExceptionInfo.none())
                .recovery(RecoveryHint.none())
                .build();
    }

    public static AsyncError unknown(Throwable throwable) {
        return AsyncError.builder()
                .classification(AsyncErrorClassification.of(
                        AsyncErrorCategory.UNKNOWN,
                        AsyncErrorReason.UNKNOWN,
                        AsyncErrorStage.NONE
                ))
                .exception(ExceptionInfo.from(throwable))
                .recovery(RecoveryHint.none())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public AsyncError copy() {
        AsyncError error = new AsyncError();
        error.classification = classification == null ? AsyncErrorClassification.none() : classification.copy();
        error.application = application == null ? ApplicationErrorInfo.none() : application.copy();
        error.exception = exception == null ? ExceptionInfo.none() : exception.copy();
        error.recovery = recovery == null ? RecoveryHint.none() : recovery.copy();
        error.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
        return error;
    }

    /**
     * 创建不包含原始 Throwable 的可序列化副本。
     *
     * <p>适合任务快照、管理接口、Redis 和数据库持久化。</p>
     *
     * @return 可序列化错误副本
     */
    public AsyncError copyWithoutThrowable() {
        AsyncError error = new AsyncError();
        error.classification = classification == null ? AsyncErrorClassification.none() : classification.copy();
        error.application = application == null ? ApplicationErrorInfo.none() : application.copy();
        error.exception = exception == null ? ExceptionInfo.none() : exception.copyWithoutThrowable();
        error.recovery = recovery == null ? RecoveryHint.none() : recovery.copy();
        error.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
        return error;
    }

    public boolean isNone() {
        return classification == null
                || classification.getCategory() == AsyncErrorCategory.NONE;
    }

    public AsyncErrorClassification getClassification() {
        return classification;
    }

    public void setClassification(AsyncErrorClassification classification) {
        this.classification = classification == null ? AsyncErrorClassification.none() : classification;
    }

    public ApplicationErrorInfo getApplication() {
        return application;
    }

    public void setApplication(ApplicationErrorInfo application) {
        this.application = application == null ? ApplicationErrorInfo.none() : application;
    }

    public ExceptionInfo getException() {
        return exception;
    }

    public void setException(ExceptionInfo exception) {
        this.exception = exception == null ? ExceptionInfo.none() : exception;
    }

    public RecoveryHint getRecovery() {
        return recovery;
    }

    public void setRecovery(RecoveryHint recovery) {
        this.recovery = recovery == null ? RecoveryHint.none() : recovery;
    }

    public Map<String, String> getAttributes() {
        if (attributes == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(attributes);
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }

    /**
     * AsyncError 构造器。
     */
    public static class Builder {

        /**
         * 正在构造的错误对象。
         */
        private final AsyncError error = new AsyncError();

        public Builder classification(AsyncErrorClassification classification) {
            error.setClassification(classification);
            return this;
        }

        public Builder application(ApplicationErrorInfo application) {
            error.setApplication(application);
            return this;
        }

        public Builder exception(ExceptionInfo exception) {
            error.setException(exception);
            return this;
        }

        public Builder recovery(RecoveryHint recovery) {
            error.setRecovery(recovery);
            return this;
        }

        public Builder attribute(String key, String value) {
            if (key != null && value != null) {
                error.attributes.put(key, value);
            }
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            error.setAttributes(attributes);
            return this;
        }

        public AsyncError build() {
            return error;
        }
    }
}
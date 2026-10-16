package com.xjtu.iron.concurrency.api.error;



/**
 * Java 异常信息。
 *
 * <p>
 * 用于承载异常类名、异常消息、根因异常和原始 Throwable。
 * </p>
 */
public class ExceptionInfo {

    /**
     * 解包后的异常类名。
     *
     * <p>
     * 例如 AsyncTaskException、ConcurrencyRejectedException。
     * </p>
     */
    private String errorClass;

    /**
     * 解包后的异常消息。
     */
    private String errorMessage;

    /**
     * 根因异常类名。
     *
     * <p>
     * 例如真正的 BizException、RpcException、SQLException。
     * </p>
     */
    private String rootErrorClass;

    /**
     * 根因异常消息。
     */
    private String rootErrorMessage;

    /**
     * 原始异常对象。
     *
     * <p>
     * 该字段只适合内存事件和监听器使用。
     * 不建议序列化到接口响应、Redis、DB。
     * </p>
     */
    private transient Throwable throwable;

    public static ExceptionInfo none() {
        return new ExceptionInfo();
    }

    public static ExceptionInfo from(Throwable throwable) {
        ExceptionInfo info = new ExceptionInfo();

        if (throwable == null) {
            return info;
        }

        Throwable unwrapped = CompletableFutureExceptionUtils.unwrap(throwable);
        Throwable root = CompletableFutureExceptionUtils.rootCause(throwable);

        info.errorClass = unwrapped == null ? null : unwrapped.getClass().getName();
        info.errorMessage = unwrapped == null ? null : unwrapped.getMessage();
        info.rootErrorClass = root == null ? null : root.getClass().getName();
        info.rootErrorMessage = root == null ? null : root.getMessage();
        info.throwable = throwable;

        return info;
    }

    public ExceptionInfo copy() {
        ExceptionInfo info = new ExceptionInfo();
        info.errorClass = errorClass;
        info.errorMessage = errorMessage;
        info.rootErrorClass = rootErrorClass;
        info.rootErrorMessage = rootErrorMessage;
        info.throwable = throwable;
        return info;
    }

    /**
     * 创建不包含原始 Throwable 的可序列化副本。
     *
     * <p>任务状态快照、REST 接口和持久化对象应使用该副本，避免序列化完整异常对象。</p>
     *
     * @return 不携带 Throwable 的异常摘要
     */
    public ExceptionInfo copyWithoutThrowable() {
        ExceptionInfo info = new ExceptionInfo();
        info.errorClass = errorClass;
        info.errorMessage = errorMessage;
        info.rootErrorClass = rootErrorClass;
        info.rootErrorMessage = rootErrorMessage;
        info.throwable = null;
        return info;
    }

    public String getErrorClass() {
        return errorClass;
    }

    public void setErrorClass(String errorClass) {
        this.errorClass = errorClass;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRootErrorClass() {
        return rootErrorClass;
    }

    public void setRootErrorClass(String rootErrorClass) {
        this.rootErrorClass = rootErrorClass;
    }

    public String getRootErrorMessage() {
        return rootErrorMessage;
    }

    public void setRootErrorMessage(String rootErrorMessage) {
        this.rootErrorMessage = rootErrorMessage;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }
}

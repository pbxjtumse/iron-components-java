package com.xjtu.iron.concurrency.demo.error;

/**
 * Demo 领域异常。
 *
 * <p>
 * 真实项目中该异常应定义在对应业务领域模块，而不是 concurrency-component。
 * </p>
 */
public final class DemoDomainException extends RuntimeException {

    /**
     * 应用侧错误码。
     */
    private final String errorCode;

    /**
     * 应用侧场景码。
     */
    private final String sceneCode;

    public DemoDomainException(
            String errorCode,
            String sceneCode,
            String message
    ) {
        super(message);
        this.errorCode = errorCode;
        this.sceneCode = sceneCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getSceneCode() {
        return sceneCode;
    }
}

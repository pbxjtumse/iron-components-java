package com.xjtu.iron.foundation.serialization;

/**
 * 序列化组件异常。
 */
public final class SerializationException extends RuntimeException {

    private final SerializationOperation operation;

    public SerializationException(SerializationOperation operation, String message, Throwable cause) {
        super(message, cause);
        this.operation = operation;
    }

    public SerializationOperation getOperation() { return operation; }
}

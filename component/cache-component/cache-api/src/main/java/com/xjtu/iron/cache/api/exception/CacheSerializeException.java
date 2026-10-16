package com.xjtu.iron.cache.api.exception;

/**
 * 缓存序列化或反序列化失败时抛出的异常。
 */
public class CacheSerializeException extends CacheException {

    /** 创建带原始异常原因的序列化异常。 */
    public CacheSerializeException(String message, Throwable cause) { super(message, cause); }
}

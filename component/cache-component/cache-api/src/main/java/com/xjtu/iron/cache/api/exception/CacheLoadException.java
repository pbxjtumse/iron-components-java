package com.xjtu.iron.cache.api.exception;

/**
 * loader 加载源数据失败时抛出的异常。
 */
public class CacheLoadException extends CacheException {

    /** 创建带原始异常原因的加载异常。 */
    public CacheLoadException(String message, Throwable cause) { super(message, cause); }
}

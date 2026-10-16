package com.xjtu.iron.cache.api.exception;

/**
 * 缓存组件基础运行时异常。
 *
 * <p>组件内部无法恢复的错误统一包装为 CacheException 或其子类。</p>
 */
public class CacheException extends RuntimeException {

    /** 创建只有消息的缓存异常。 */
    public CacheException(String message) { super(message); }

    /** 创建带原始异常原因的缓存异常。 */
    public CacheException(String message, Throwable cause) { super(message, cause); }
}

package com.xjtu.iron.storage.routing.api;

/**
 * 存储路由异常。
 *
 * <p>该异常用于表达路由参数非法、路由规则缺失、未知 route 等问题。
 * 它只描述“无法决定去哪”，不负责包装 JDBC / SQL 执行异常。</p>
 */
public class StorageRoutingException extends RuntimeException {

    public StorageRoutingException(String message) {
        super(message);
    }

    public StorageRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}

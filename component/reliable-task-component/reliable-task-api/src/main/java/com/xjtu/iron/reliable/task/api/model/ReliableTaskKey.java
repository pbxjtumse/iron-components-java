package com.xjtu.iron.reliable.task.api.model;

import java.util.Objects;

/** 一条任务在逻辑 Store 中的稳定主键。 */
public final class ReliableTaskKey {

    /** 未显式指定 Store 时使用的默认逻辑存储名称。 */
    public static final String DEFAULT_STORE_NAME = "default";

    /** 未显式指定命名空间时使用的默认命名空间。 */
    public static final String DEFAULT_NAMESPACE = "default";

    /** 任务所属的逻辑存储名称。 */
    private final String storeName;

    /** 任务所属的业务命名空间。 */
    private final String namespace;

    /** 命名空间内稳定且唯一的任务编号。 */
    private final String taskId;

    public ReliableTaskKey(String storeName, String namespace, String taskId) {
        this.storeName = requireText(storeName, "storeName");
        this.namespace = requireText(namespace, "namespace");
        this.taskId = requireText(taskId, "taskId");
    }

    public static ReliableTaskKey defaults(String taskId) {
        return new ReliableTaskKey(DEFAULT_STORE_NAME, DEFAULT_NAMESPACE, taskId);
    }

    public String getStoreName() {
        return storeName;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getTaskId() {
        return taskId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ReliableTaskKey that)) {
            return false;
        }
        return storeName.equals(that.storeName)
                && namespace.equals(that.namespace)
                && taskId.equals(that.taskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storeName, namespace, taskId);
    }

    @Override
    public String toString() {
        return "ReliableTaskKey{" +
                "storeName='" + storeName + '\'' +
                ", namespace='" + namespace + '\'' +
                ", taskId='" + taskId + '\'' +
                '}';
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

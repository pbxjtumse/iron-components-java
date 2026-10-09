package com.xjtu.iron.reliable.task.api.model;

/** 一条任务在逻辑 Store 中的稳定主键。 */
public record ReliableTaskKey(String storeName, String namespace, String taskId) {

    public static final String DEFAULT_STORE_NAME = "default";
    public static final String DEFAULT_NAMESPACE = "default";

    public ReliableTaskKey {
        storeName = requireText(storeName, "storeName");
        namespace = requireText(namespace, "namespace");
        taskId = requireText(taskId, "taskId");
    }

    public static ReliableTaskKey defaults(String taskId) {
        return new ReliableTaskKey(DEFAULT_STORE_NAME, DEFAULT_NAMESPACE, taskId);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}

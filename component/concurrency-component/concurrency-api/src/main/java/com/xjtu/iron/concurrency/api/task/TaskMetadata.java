

package com.xjtu.iron.concurrency.api.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异步任务基础元数据。
 */
public final class TaskMetadata {

    /**
     * 任务唯一 ID。
     */
    private final String taskId;

    /**
     * 线程池名称。
     */
    private final String executorName;

    /**
     * 任务名称。
     */
    private final String taskName;

    /**
     * 业务标识。
     */
    private final String bizKey;

    /**
     * 任务描述。
     */
    private final String description;

    /**
     * 任务标签。
     */
    private final Map<String, String> tags;

    public TaskMetadata(
            String taskId,
            String executorName,
            String taskName,
            String bizKey,
            String description,
            Map<String, String> tags
    ) {
        this.taskId = taskId;
        this.executorName = executorName;
        this.taskName = taskName;
        this.bizKey = bizKey;
        this.description = description;
        this.tags = tags == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(tags));
    }

    public String getTaskId() {
        return taskId;
    }

    public String getExecutorName() {
        return executorName;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getBizKey() {
        return bizKey;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getTags() {
        return tags;
    }
}

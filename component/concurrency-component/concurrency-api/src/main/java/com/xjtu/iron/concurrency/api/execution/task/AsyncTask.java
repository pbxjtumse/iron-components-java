package com.xjtu.iron.concurrency.api.execution.task;

import com.xjtu.iron.concurrency.api.retry.RetryPolicy;
import com.xjtu.iron.concurrency.api.task.TaskMetadata;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 异步任务定义。
 *
 * <p>
 * 描述用户提交的一次异步任务，包括任务身份、原始执行逻辑、超时、fallback、
 * 上下文传播和预留重试策略等配置。
 * </p>
 *
 * <p>
 * 该对象保存的是用户提交的原始 {@link #operation}；经过 MDC、TTL 等上下文装饰后
 * 真正在线程池中运行的逻辑，由 concurrency-core 中的 TaskExecutionContext 保存。
 * </p>
 *
 * <p>
 * 任务提交后不应继续修改本对象。组件会在创建执行上下文时固定一份
 * {@link TaskMetadata}，保证后续生命周期事件的任务信息一致。
 * </p>
 *
 * @param <T> 任务返回值类型
 */
public class AsyncTask<T> {

    /**
     * 任务唯一 ID。
     *
     * <p>未指定时会在创建或校验阶段自动生成。</p>
     */
    private String taskId;

    /**
     * 执行该任务的线程池名称。
     */
    private String executorName;

    /**
     * 任务名称。
     *
     * <p>用于日志、指标、监听器和任务状态查询，不应包含订单号等高基数数据。</p>
     */
    private String taskName;

    /**
     * 业务标识。
     *
     * <p>例如 orderId=xxx、userId=xxx，用于排查与补偿，不建议作为指标标签。</p>
     */
    private String bizKey;

    /**
     * 任务描述。
     */
    private String description;

    /**
     * 任务标签。
     *
     * <p>适合保存 scene、source、tenant 等低基数字段。</p>
     */
    private Map<String, String> tags = new LinkedHashMap<>();

    /**
     * 结果层超时时间。
     *
     * <p>控制调用方等待结果的最长时间，不保证底层运行线程一定停止。</p>
     */
    private Duration timeout;

    /**
     * 排队超时时间。
     *
     * <p>任务在线程池队列中等待超过该时间后，不再执行原始任务逻辑。</p>
     */
    private Duration queueTimeout;

    /**
     * 结果层超时后是否尝试取消底层任务。
     */
    private boolean cancelOnTimeout;

    /**
     * 取消或超时后是否向运行线程发送 interrupt。
     *
     * <p>Java 中断是协作式的，不是强制终止。</p>
     */
    private boolean interruptOnTimeout;

    /**
     * 用户提交的原始业务执行逻辑。
     *
     * <p>该对象尚未经过 MDC、TTL 等上下文传播包装。</p>
     */
    private Supplier<T> operation;

    /**
     * 原始任务失败、拒绝或超时后的 fallback 逻辑。
     */
    private Function<Throwable, T> fallback;

    /**
     * 是否启用上下文传播，默认启用。
     */
    private boolean contextPropagation = true;

    /**
     * 重试策略占位。
     *
     * <p>一期仅保存配置，不在并行组件内部执行复杂重试。</p>
     */
    private RetryPolicy retryPolicy = RetryPolicy.none();

    /**
     * 创建异步任务。
     *
     * @param executorName 线程池名称
     * @param taskName 任务名称
     * @param operation 原始任务执行逻辑
     * @param <T> 返回值类型
     * @return 异步任务定义
     */
    public static <T> AsyncTask<T> of(String executorName, String taskName, Supplier<T> operation) {
        AsyncTask<T> task = new AsyncTask<>();
        task.taskId = UUID.randomUUID().toString();
        task.executorName = executorName;
        task.taskName = taskName;
        task.operation = operation;
        return task;
    }

    /**
     * 设置任务 ID。
     *
     * @param taskId 任务唯一 ID
     * @return 当前任务定义
     */
    public AsyncTask<T> taskId(String taskId) {
        this.taskId = taskId;
        return this;
    }

    /**
     * 设置业务标识。
     *
     * @param bizKey 业务标识
     * @return 当前任务定义
     */
    public AsyncTask<T> bizKey(String bizKey) {
        this.bizKey = bizKey;
        return this;
    }

    /**
     * 设置任务描述。
     *
     * @param description 任务描述
     * @return 当前任务定义
     */
    public AsyncTask<T> description(String description) {
        this.description = description;
        return this;
    }

    /**
     * 增加单个任务标签。
     *
     * @param key 标签名
     * @param value 标签值
     * @return 当前任务定义
     */
    public AsyncTask<T> tag(String key, String value) {
        if (key != null && !key.isBlank() && value != null) {
            this.tags.put(key, value);
        }
        return this;
    }

    /**
     * 批量设置任务标签。
     *
     * @param tags 标签集合
     * @return 当前任务定义
     */
    public AsyncTask<T> tags(Map<String, String> tags) {
        setTags(tags);
        return this;
    }

    /**
     * 设置结果层超时时间。
     *
     * @param timeout 结果层超时时间
     * @return 当前任务定义
     */
    public AsyncTask<T> timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * 设置排队超时时间。
     *
     * @param queueTimeout 排队超时时间
     * @return 当前任务定义
     */
    public AsyncTask<T> queueTimeout(Duration queueTimeout) {
        this.queueTimeout = queueTimeout;
        return this;
    }

    /**
     * 设置结果超时后是否尝试取消底层任务。
     *
     * @param cancelOnTimeout 是否尝试取消
     * @return 当前任务定义
     */
    public AsyncTask<T> cancelOnTimeout(boolean cancelOnTimeout) {
        this.cancelOnTimeout = cancelOnTimeout;
        return this;
    }

    /**
     * 设置取消时是否尝试中断运行线程。
     *
     * @param interruptOnCancel 是否发送 interrupt
     * @return 当前任务定义
     */
    public AsyncTask<T> interruptOnCancel(boolean interruptOnCancel) {
        this.interruptOnTimeout = interruptOnCancel;
        return this;
    }

    /**
     * 设置 fallback 逻辑。
     *
     * @param fallback fallback 函数
     * @return 当前任务定义
     */
    public AsyncTask<T> fallback(Function<Throwable, T> fallback) {
        this.fallback = fallback;
        return this;
    }

    /**
     * 设置是否传播线程上下文。
     *
     * @param contextPropagation 是否传播上下文
     * @return 当前任务定义
     */
    public AsyncTask<T> contextPropagation(boolean contextPropagation) {
        this.contextPropagation = contextPropagation;
        return this;
    }

    /**
     * 设置预留重试策略。
     *
     * @param retryPolicy 重试策略
     * @return 当前任务定义
     */
    public AsyncTask<T> retryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy == null ? RetryPolicy.none() : retryPolicy;
        return this;
    }

    /**
     * 校验任务定义并补齐默认值。
     */
    public void validate() {
        if (taskId == null || taskId.isBlank()) {
            taskId = UUID.randomUUID().toString();
        }

        if (executorName == null || executorName.isBlank()) {
            throw new IllegalArgumentException("executorName must not be blank");
        }

        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("taskName must not be blank");
        }

        Objects.requireNonNull(operation, "operation must not be null");

        if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
            throw new IllegalArgumentException("timeout must be greater than zero");
        }

        if (queueTimeout != null && (queueTimeout.isNegative() || queueTimeout.isZero())) {
            throw new IllegalArgumentException("queueTimeout must be greater than zero");
        }

        if (interruptOnTimeout && !cancelOnTimeout) {
            throw new IllegalArgumentException(
                    "interruptOnTimeout requires cancelOnTimeout=true"
            );
        }

        if (retryPolicy == null) {
            retryPolicy = RetryPolicy.none();
        }
        retryPolicy.validate();
    }

    /**
     * 生成当前任务的只读元数据快照。
     *
     * <p>
     * 元数据不包含 operation、fallback 和 RetryPolicy 等执行对象。
     * 调用前应先完成 {@link #validate()}。
     * </p>
     *
     * @return 任务元数据快照
     */
    public TaskMetadata metadata() {
        return new TaskMetadata(
                taskId,
                executorName,
                taskName,
                bizKey,
                description,
                tags
        );
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getExecutorName() {
        return executorName;
    }

    public void setExecutorName(String executorName) {
        this.executorName = executorName;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取不可修改的任务标签视图。
     *
     * @return 只读标签集合
     */
    public Map<String, String> getTags() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(tags));
    }

    /**
     * 设置任务标签并进行防御性复制。
     *
     * @param tags 标签集合
     */
    public void setTags(Map<String, String> tags) {
        this.tags = tags == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tags);
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration getQueueTimeout() {
        return queueTimeout;
    }

    public void setQueueTimeout(Duration queueTimeout) {
        this.queueTimeout = queueTimeout;
    }

    public boolean isCancelOnTimeout() {
        return cancelOnTimeout;
    }

    public void setCancelOnTimeout(boolean cancelOnTimeout) {
        this.cancelOnTimeout = cancelOnTimeout;
    }

    public boolean isInterruptOnTimeout() {
        return interruptOnTimeout;
    }

    public void setInterruptOnTimeout(boolean interruptOnTimeout) {
        this.interruptOnTimeout = interruptOnTimeout;
    }

    public Supplier<T> getOperation() {
        return operation;
    }

    public void setOperation(Supplier<T> operation) {
        this.operation = operation;
    }

    public Function<Throwable, T> getFallback() {
        return fallback;
    }

    public void setFallback(Function<Throwable, T> fallback) {
        this.fallback = fallback;
    }

    public boolean isContextPropagation() {
        return contextPropagation;
    }

    public void setContextPropagation(boolean contextPropagation) {
        this.contextPropagation = contextPropagation;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public void setRetryPolicy(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy == null ? RetryPolicy.none() : retryPolicy;
    }
}

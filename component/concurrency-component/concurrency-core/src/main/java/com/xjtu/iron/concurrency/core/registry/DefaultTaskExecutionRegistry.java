package com.xjtu.iron.concurrency.core.registry;

import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionRegistry;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于内存的任务状态注册表。
 *
 * <p>
 * 使用 ConcurrentHashMap 保存 taskId 的最新快照，使用带版本号的双端队列维护最近更新时间顺序。
 * 更新操作不再对全部快照进行排序；淘汰和 recent 查询会忽略旧版本索引。
 * </p>
 *
 * <p>
 * 一期用于当前 JVM 的最近任务诊断。后续跨节点任务状态查询应替换为 Redis、DB 或独立任务中心。
 * </p>
 */
public final class DefaultTaskExecutionRegistry
        implements TaskExecutionRegistry {

    /**
     * taskId 到最新版本快照的映射。
     */
    private final ConcurrentMap<String, VersionedSnapshot> snapshots =
            new ConcurrentHashMap<>();

    /**
     * 最近更新时间索引，队首是最新版本。
     *
     * <p>
     * 同一个 taskId 多次更新会留下旧索引；查询和淘汰通过版本号过滤，后台压缩负责清理积累的旧索引。
     * </p>
     */
    private final ConcurrentLinkedDeque<IndexEntry> updateOrder =
            new ConcurrentLinkedDeque<>();

    /**
     * 单调递增版本号。
     */
    private final AtomicLong sequence = new AtomicLong();

    /**
     * 顺序索引近似元素数量。
     *
     * <p>
     * ConcurrentLinkedDeque.size() 需要线性遍历，因此使用独立计数避免每次 update 都扫描整个索引。
     * </p>
     */
    private final AtomicLong indexSize = new AtomicLong();

    /**
     * 最多保留的不同 taskId 数量。
     */
    private final int maxSize;

    public DefaultTaskExecutionRegistry() {
        this(10_000);
    }

    public DefaultTaskExecutionRegistry(int maxSize) {
        this.maxSize = Math.max(maxSize, 100);
    }

    @Override
    public void update(TaskExecutionSnapshot snapshot) {
        if (snapshot == null
                || snapshot.getTask() == null
                || snapshot.getTaskId() == null
                || snapshot.getTaskId().isBlank()) {
            return;
        }

        long version = sequence.incrementAndGet();
        String taskId = snapshot.getTaskId();
        VersionedSnapshot versioned = new VersionedSnapshot(version, snapshot);

        snapshots.put(taskId, versioned);
        updateOrder.addFirst(new IndexEntry(taskId, version));
        indexSize.incrementAndGet();

        evictIfNecessary();
        cleanupStaleTail(16);
    }

    @Override
    public Optional<TaskExecutionSnapshot> get(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }

        VersionedSnapshot versioned = snapshots.get(taskId);
        return versioned == null
                ? Optional.empty()
                : Optional.of(versioned.snapshot());
    }

    @Override
    public List<TaskExecutionSnapshot> recent(int limit) {
        int actualLimit = limit <= 0
                ? Math.min(100, maxSize)
                : Math.min(limit, maxSize);
        List<TaskExecutionSnapshot> result = new ArrayList<>(actualLimit);

        for (IndexEntry entry : updateOrder) {
            VersionedSnapshot current = snapshots.get(entry.taskId());

            /*
             * 只有索引版本与 Map 中最新版本一致时才返回，旧状态索引自动跳过。
             */
            if (current == null || current.version() != entry.version()) {
                continue;
            }

            result.add(current.snapshot());
            if (result.size() >= actualLimit) {
                break;
            }
        }

        return List.copyOf(result);
    }

    /**
     * 超过容量时从最旧索引开始淘汰。
     *
     * <p>
     * 使用版本比较删除，避免旧索引误删同一 taskId 的最新快照。
     * </p>
     */
    private void evictIfNecessary() {
        while (snapshots.size() > maxSize) {
            IndexEntry oldest = updateOrder.pollLast();
            if (oldest == null) {
                indexSize.set(0L);
                return;
            }
            indexSize.decrementAndGet();

            snapshots.computeIfPresent(oldest.taskId(), (taskId, current) ->
                    current.version() == oldest.version()
                            ? null
                            : current
            );
        }
    }

    /**
     * 从队尾有限量清理已经失效的旧版本索引。
     *
     * <p>
     * 这里刻意不再使用 updateOrder.clear() + addAll(...) 做全量压缩。
     * 原来的全量压缩会和并发 update 交错，可能清掉刚刚 addFirst 的最新索引，
     * 导致 Map 中存在最新快照，但 recent() 因顺序索引丢失而查不到。
     * </p>
     *
     * @param maxCleanup 单次最多清理多少个旧尾节点
     */
    private void cleanupStaleTail(int maxCleanup) {
        for (int i = 0; i < maxCleanup; i++) {
            IndexEntry tail = updateOrder.peekLast();
            if (tail == null) {
                indexSize.set(0L);
                return;
            }

            VersionedSnapshot current = snapshots.get(tail.taskId());
            boolean stale = current == null || current.version() != tail.version();
            if (!stale) {
                return;
            }

            if (updateOrder.pollLast() != null) {
                indexSize.decrementAndGet();
            }
        }
    }

    /**
     * Map 中保存的最新版本快照。
     */
    private record VersionedSnapshot(
            long version,
            TaskExecutionSnapshot snapshot
    ) {
    }

    /**
     * 最近更新时间索引项。
     */
    private record IndexEntry(
            String taskId,
            long version
    ) {
    }
}

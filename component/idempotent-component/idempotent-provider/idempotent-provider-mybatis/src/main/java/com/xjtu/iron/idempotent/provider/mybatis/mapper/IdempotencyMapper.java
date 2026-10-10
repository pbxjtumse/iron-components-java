package com.xjtu.iron.idempotent.provider.mybatis.mapper;

import com.xjtu.iron.idempotent.provider.mybatis.mapping.IdempotencyRow;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/** 幂等技术表固定状态迁移的 MyBatis Mapper。 */
public interface IdempotencyMapper {

    /** 插入首个 PROCESSING generation。 */
    int insertProcessing(@Param("table") String table, @Param("row") IdempotencyRow row);

    /** 查询当前状态快照。 */
    IdempotencyRow select(
            @Param("table") String table,
            @Param("storeName") String storeName,
            @Param("namespace") String namespace,
            @Param("key") String key
    );

    /** 查询并锁定当前状态行。 */
    IdempotencyRow selectForUpdate(
            @Param("table") String table,
            @Param("storeName") String storeName,
            @Param("namespace") String namespace,
            @Param("key") String key
    );

    /** 过期 WINDOWED 记录开启新 generation。 */
    int restartWindow(
            @Param("table") String table,
            @Param("row") IdempotencyRow row,
            @Param("expectedVersion") long expectedVersion
    );

    /** Recovery 使用新 owner 接管旧 generation。 */
    int reacquire(
            @Param("table") String table,
            @Param("row") IdempotencyRow row,
            @Param("expectedVersion") long expectedVersion
    );

    /** 延长滑动窗口，不改变 owner、status 和 version。 */
    int touchWindow(
            @Param("table") String table,
            @Param("row") IdempotencyRow row,
            @Param("expectedVersion") long expectedVersion
    );

    /** 条件写入 SUCCESS 或 DISCARDED 终态。 */
    int complete(
            @Param("table") String table,
            @Param("row") IdempotencyRow row,
            @Param("expectedOwner") String expectedOwner,
            @Param("expectedVersion") long expectedVersion
    );

    /** 条件写入 FAILED 终态。 */
    int fail(
            @Param("table") String table,
            @Param("row") IdempotencyRow row,
            @Param("expectedOwner") String expectedOwner,
            @Param("expectedVersion") long expectedVersion
    );

    /** 查询当前物理分片中的恢复候选。 */
    List<IdempotencyRow> findRecoveryCandidates(
            @Param("table") String table,
            @Param("storeName") String storeName,
            @Param("scanBucket") int scanBucket,
            @Param("namespace") String namespace,
            @Param("now") Instant now,
            @Param("limit") int limit
    );
}

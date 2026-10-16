package com.xjtu.iron.reliable.task.provider.mybatis.mapper;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.provider.mybatis.mapping.ReliableTaskRow;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/** Reliable Task 固定表 SQL 的 MyBatis Mapper。 */
public interface ReliableTaskMapper {

    int insert(
            @Param("tableName") String tableName,
            @Param("task") ReliableTask task
    );

    ReliableTaskRow find(
            @Param("tableName") String tableName,
            @Param("storeName") String storeName,
            @Param("namespace") String namespace,
            @Param("taskId") String taskId
    );

    List<ReliableTaskRow> findDue(
            @Param("tableName") String tableName,
            @Param("storeName") String storeName,
            @Param("scanBucket") int scanBucket,
            @Param("now") Instant now,
            @Param("limit") int limit
    );

    int tryClaim(
            @Param("tableName") String tableName,
            @Param("candidate") ReliableTask candidate,
            @Param("ownerId") String ownerId,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil,
            @Param("expiredRunning") boolean expiredRunning
    );

    int transition(
            @Param("tableName") String tableName,
            @Param("storeName") String storeName,
            @Param("namespace") String namespace,
            @Param("taskId") String taskId,
            @Param("ownerId") String ownerId,
            @Param("expectedVersion") long expectedVersion,
            @Param("targetStatus") String targetStatus,
            @Param("nextExecuteAt") Instant nextExecuteAt,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("now") Instant now,
            @Param("completedAt") Instant completedAt
    );

    int renewLease(
            @Param("tableName") String tableName,
            @Param("storeName") String storeName,
            @Param("namespace") String namespace,
            @Param("taskId") String taskId,
            @Param("ownerId") String ownerId,
            @Param("expectedVersion") long expectedVersion,
            @Param("now") Instant now,
            @Param("leaseUntil") Instant leaseUntil
    );

    int adminTransition(
            @Param("tableName") String tableName,
            @Param("storeName") String storeName,
            @Param("namespace") String namespace,
            @Param("taskId") String taskId,
            @Param("expectedStatus") String expectedStatus,
            @Param("expectedVersion") long expectedVersion,
            @Param("targetStatus") String targetStatus,
            @Param("nextExecuteAt") Instant nextExecuteAt,
            @Param("resetAttempts") boolean resetAttempts,
            @Param("now") Instant now,
            @Param("completedAt") Instant completedAt
    );
}

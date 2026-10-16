package com.xjtu.iron.distributed.lock.demo.fencing;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;

/**
 * 演示业务资源如何通过 last_fencing_token 拒绝旧 owner 写入。
 */
public final class JdbcFencedDemoRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcFencedDemoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public void initializeSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS iron_lock_fenced_demo_resource ("
                + "resource_key VARCHAR(256) NOT NULL PRIMARY KEY,"
                + "payload VARCHAR(1024),"
                + "last_fencing_token BIGINT NOT NULL,"
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    /**
     * 仅当新 token 大于已保存 token 时更新。
     */
    public boolean updateIfNewer(String resourceKey, String payload, long fencingToken) {
        int updated = jdbcTemplate.update(
                "UPDATE iron_lock_fenced_demo_resource " + "SET payload = ?, last_fencing_token = ?, updated_at = CURRENT_TIMESTAMP " + "WHERE resource_key = ? AND last_fencing_token < ?",
                payload, fencingToken, resourceKey, fencingToken);
        if (updated == 1) {
            return true;
        }
        try {
            return jdbcTemplate.update(
                    "INSERT INTO iron_lock_fenced_demo_resource " + "(resource_key, payload, last_fencing_token, updated_at) " + "VALUES (?, ?, ?, CURRENT_TIMESTAMP)",
                    resourceKey, payload, fencingToken) == 1;
        } catch (DuplicateKeyException duplicate) {
            /*
             * 可能是另一个 owner 在“首次建行”阶段抢先 INSERT。不能直接判定当前 token 已过期，
             * 因为当前 token 也可能更大。重新执行一次带 fencing 条件的 UPDATE：
             * - 当前 token 更大：更新成功；
             * - 当前 token 更小或相等：更新 0 行，明确拒绝旧写。
             */
            return jdbcTemplate.update(
                    "UPDATE iron_lock_fenced_demo_resource " + "SET payload = ?, last_fencing_token = ?, updated_at = CURRENT_TIMESTAMP " + "WHERE resource_key = ? AND last_fencing_token < ?",
                    payload, fencingToken, resourceKey, fencingToken) == 1;
        }
    }

    public long currentToken(String resourceKey) {
        Long value = jdbcTemplate.queryForObject("SELECT last_fencing_token FROM iron_lock_fenced_demo_resource WHERE resource_key = ?", Long.class,
                resourceKey);
        return value == null ? 0L : value;
    }

    public String currentPayload(String resourceKey) {
        return jdbcTemplate.queryForObject("SELECT payload FROM iron_lock_fenced_demo_resource WHERE resource_key = ?", String.class, resourceKey);
    }

    public void delete(String resourceKey) {
        jdbcTemplate.update("DELETE FROM iron_lock_fenced_demo_resource WHERE resource_key = ?", resourceKey);
    }
}

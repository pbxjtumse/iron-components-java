package com.xjtu.iron.reliable.task.provider.testkit;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** 为 Provider 契约测试创建与 MySQL 语义兼容的 H2 表结构。 */
public final class ReliableTaskTestSchema {

    private ReliableTaskTestSchema() {
    }

    public static void create(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE iron_reliable_task (
                        store_name VARCHAR(64) NOT NULL,
                        namespace VARCHAR(64) NOT NULL,
                        task_id VARCHAR(128) NOT NULL,
                        task_type VARCHAR(64) NOT NULL,
                        business_key VARCHAR(128),
                        payload CLOB NOT NULL,
                        route_key VARCHAR(256),
                        scan_bucket INT NOT NULL,
                        status VARCHAR(32) NOT NULL,
                        attempt_count INT NOT NULL,
                        max_attempts INT NOT NULL,
                        next_execute_at TIMESTAMP(6),
                        owner_id VARCHAR(128),
                        lease_until TIMESTAMP(6),
                        version BIGINT NOT NULL,
                        last_error_code VARCHAR(64),
                        last_error_message VARCHAR(1024),
                        created_at TIMESTAMP(6) NOT NULL,
                        updated_at TIMESTAMP(6) NOT NULL,
                        completed_at TIMESTAMP(6),
                        PRIMARY KEY (store_name, namespace, task_id)
                    )
                    """);
        }
    }
}

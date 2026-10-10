CREATE TABLE IF NOT EXISTS iron_reliable_task (
    store_name VARCHAR(64) NOT NULL,
    namespace VARCHAR(64) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    business_key VARCHAR(128),
    payload CLOB NOT NULL,
    route_key VARCHAR(256),
    scan_bucket INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL,
    next_execute_at TIMESTAMP,
    owner_id VARCHAR(128),
    lease_until TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64),
    last_error_message VARCHAR(1024),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    PRIMARY KEY (store_name, namespace, task_id)
);

CREATE INDEX IF NOT EXISTS idx_irt_due
    ON iron_reliable_task(store_name, scan_bucket, status, next_execute_at);
CREATE INDEX IF NOT EXISTS idx_irt_lease
    ON iron_reliable_task(store_name, scan_bucket, status, lease_until);

CREATE TABLE IF NOT EXISTS iron_idempotency_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_name VARCHAR(64) NOT NULL DEFAULT 'default',
    scan_bucket INT NOT NULL DEFAULT 0,
    namespace VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    route_key VARCHAR(256) NULL,
    request_hash VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    owner_token VARCHAR(128) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    result_payload TEXT NULL,
    failure_code VARCHAR(128) NULL,
    failure_message VARCHAR(1024) NULL,
    failure_retryable BOOLEAN NOT NULL DEFAULT FALSE,
    recovery_mode VARCHAR(32) NOT NULL DEFAULT 'NONE',
    window_policy VARCHAR(64) NOT NULL DEFAULT 'FIXED_FROM_FIRST_ACQUIRE',
    processing_expire_at TIMESTAMP(3) NULL,
    window_expire_at TIMESTAMP(3) NULL,
    retention_expire_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    completed_at TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_iron_idempotency_identity (store_name, namespace, idempotency_key),
    KEY idx_iron_idempotency_recovery_scan (
        store_name, scan_bucket, recovery_mode, status, processing_expire_at, id
    )
);

CREATE TABLE IF NOT EXISTS iron_idempotency_record (
    id BIGINT NOT NULL AUTO_INCREMENT,

    -- 逻辑 Store 与 Recovery 扫描分桶；物理分片由 StorageRoute 决定。
    store_name VARCHAR(64) NOT NULL DEFAULT 'default',
    scan_bucket INT NOT NULL DEFAULT 0,

    namespace VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,

    -- 业务路由元数据，例如 tenant / merchant；不再承担存储分片职责。
    route_key VARCHAR(256) NULL,

    -- 同一个幂等身份必须对应同一个业务请求指纹。
    request_hash VARCHAR(128) NULL,

    -- V2 持久状态：PROCESSING / SUCCESS / FAILED / DISCARDED。
    status VARCHAR(32) NOT NULL,
    owner_token VARCHAR(128) NULL,
    version BIGINT NOT NULL DEFAULT 0,

    result_payload TEXT NULL,
    failure_code VARCHAR(128) NULL,
    failure_message VARCHAR(1024) NULL,
    failure_retryable BOOLEAN NOT NULL DEFAULT FALSE,

    -- NONE / EXTERNAL_TASK。扫描调度不在本组件，这里只保存恢复契约。
    recovery_mode VARCHAR(32) NOT NULL DEFAULT 'NONE',

    -- WINDOWED 窗口策略；DURABLE 下仅作为元数据存在。
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

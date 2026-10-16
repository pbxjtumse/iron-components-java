package com.xjtu.iron.distributed.lock.spi.fencing;

import com.xjtu.iron.distributed.lock.api.model.LockOptions;

import java.util.Objects;

/**
 * fencing token 生成请求。
 */
public final class FencingTokenRequest {

    /** 锁命名空间。 */
    private final String namespace;

    /** 业务锁名称。 */
    private final String lockName;

    /** ownerToken。 */
    private final String ownerToken;

    /** 锁选项。 */
    private final LockOptions options;

    private FencingTokenRequest(Builder builder) {
        this.namespace = requireText(builder.namespace, "namespace");
        this.lockName = requireText(builder.lockName, "lockName");
        this.ownerToken = requireText(builder.ownerToken, "ownerToken");
        this.options = Objects.requireNonNull(builder.options, "options must not be null");
    }

    public static Builder builder() { return new Builder(); }

    public String getNamespace() { return namespace; }
    public String getLockName() { return lockName; }
    public String getOwnerToken() { return ownerToken; }
    public LockOptions getOptions() { return options; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) { throw new IllegalArgumentException(fieldName + " must not be blank"); }
        return value.trim();
    }

    /** FencingTokenRequest 构造器。 */
    public static final class Builder {
        private String namespace;
        private String lockName;
        private String ownerToken;
        private LockOptions options;
        private Builder() {}
        public Builder namespace(String namespace) { this.namespace = namespace; return this; }
        public Builder lockName(String lockName) { this.lockName = lockName; return this; }
        public Builder ownerToken(String ownerToken) { this.ownerToken = ownerToken; return this; }
        public Builder options(LockOptions options) { this.options = options; return this; }
        public FencingTokenRequest build() { return new FencingTokenRequest(this); }
    }
}

package com.xjtu.iron.distributed.lock.spi.protocol.acquire;

import com.xjtu.iron.distributed.lock.api.model.LockOptions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Provider 加锁请求。
 *
 * <p>该请求只包含 Provider 通用信息，不包含 Redis lockKey、fencingKey、ZK path 这类底层物理标识。
 * 物理 key/path 应由具体 Provider 自己构造，避免 Redis 细节污染通用 SPI。</p>
 */
public final class LockAcquireRequest {


    /** 业务锁名称。 */
    private final String lockName;

    /** 本次加锁请求生成的 ownerToken。 */
    private final String ownerToken;

    /** 锁选项。 */
    private final LockOptions options;

    /** 是否要求 LockProvider 在原子加锁流程中生成原生 fencing token。 */
    private final boolean nativeFencingRequired;

    /** 扩展属性。 */
    private final Map<String, String> attributes;

    private LockAcquireRequest(Builder builder) {
        this.lockName = requireText(builder.lockName, "lockName");
        this.ownerToken = requireText(builder.ownerToken, "ownerToken");
        this.options = Objects.requireNonNull(builder.options, "options must not be null");
        /*
         * 默认必须是 false。
         *
         * fencingRequired 表示“业务需要 token”；nativeFencingRequired 表示“本次 acquire
         * 是否要求 LockProvider 原生发号”。二者不是一回事。是否原生发号只能由
         * FencingTokenCoordinator 选出的 FencingTokenPlan 决定，不能简单等于 options.isFencingRequired()。
         */
        this.nativeFencingRequired = builder.nativeFencingRequired != null
                && builder.nativeFencingRequired;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getLockName() {
        return lockName;
    }

    public String getOwnerToken() {
        return ownerToken;
    }

    public LockOptions getOptions() {
        return options;
    }

    public String getNamespace() {
        return options.getNamespace();
    }

    public boolean isNativeFencingRequired() {
        return nativeFencingRequired;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    /** LockAcquireRequest 构造器。 */
    public static final class Builder {
        private String lockName;
        private String ownerToken;
        private LockOptions options;
        private Boolean nativeFencingRequired;
        private Map<String, String> attributes = new LinkedHashMap<>();

        private Builder() {
        }


        public Builder lockName(String lockName) {
            this.lockName = lockName;
            return this;
        }

        public Builder ownerToken(String ownerToken) {
            this.ownerToken = ownerToken;
            return this;
        }

        public Builder options(LockOptions options) {
            this.options = options;
            return this;
        }

        public Builder nativeFencingRequired(boolean nativeFencingRequired) {
            this.nativeFencingRequired = nativeFencingRequired;
            return this;
        }

        public Builder attribute(String key, String value) {
            if (key != null && value != null) {
                this.attributes.put(key, value);
            }
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
            return this;
        }

        public LockAcquireRequest build() {
            return new LockAcquireRequest(this);
        }
    }
}

package com.xjtu.iron.idempotent.api.repository;

import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;

/**
 * Repository 显式能力描述。
 *
 * <p>Core 不通过“Redis/JDBC 名称”猜语义，而由 Provider 自己声明：
 * 支持 WINDOWED/DURABLE、能否保存结果 payload、能否参与业务本地事务、能否提供恢复扫描。</p>
 */
public final class IdempotencyRepositoryCapabilities {

    private final boolean windowedSupported;
    private final boolean durableSupported;
    private final boolean resultPayloadSupported;
    private final boolean businessTransactionParticipationSupported;
    private final boolean recoveryQuerySupported;

    private IdempotencyRepositoryCapabilities(Builder builder) {
        this.windowedSupported = builder.windowedSupported;
        this.durableSupported = builder.durableSupported;
        this.resultPayloadSupported = builder.resultPayloadSupported;
        this.businessTransactionParticipationSupported = builder.businessTransactionParticipationSupported;
        this.recoveryQuerySupported = builder.recoveryQuerySupported;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean supports(IdempotencyMode mode) {
        if (mode == null) {
            return false;
        }
        return mode.isWindowed() ? windowedSupported : durableSupported;
    }

    public boolean isWindowedSupported() { return windowedSupported; }
    public boolean isDurableSupported() { return durableSupported; }
    public boolean isResultPayloadSupported() { return resultPayloadSupported; }
    public boolean isBusinessTransactionParticipationSupported() {
        return businessTransactionParticipationSupported;
    }
    public boolean isRecoveryQuerySupported() { return recoveryQuerySupported; }

    public static final class Builder {
        private boolean windowedSupported;
        private boolean durableSupported;
        private boolean resultPayloadSupported;
        private boolean businessTransactionParticipationSupported;
        private boolean recoveryQuerySupported;

        public Builder windowedSupported(boolean value) {
            this.windowedSupported = value;
            return this;
        }

        public Builder durableSupported(boolean value) {
            this.durableSupported = value;
            return this;
        }

        public Builder resultPayloadSupported(boolean value) {
            this.resultPayloadSupported = value;
            return this;
        }

        public Builder businessTransactionParticipationSupported(boolean value) {
            this.businessTransactionParticipationSupported = value;
            return this;
        }

        public Builder recoveryQuerySupported(boolean value) {
            this.recoveryQuerySupported = value;
            return this;
        }

        public IdempotencyRepositoryCapabilities build() {
            return new IdempotencyRepositoryCapabilities(this);
        }
    }
}

package com.xjtu.iron.distributed.lock.spi;

import com.xjtu.iron.distributed.lock.api.model.LockAutoRenewMode;

/**
 * Provider 能力描述。
 *
 * <p>能力描述的是“通过 iron-lock 统一 SPI 真正暴露出来的能力”，而不是底层库理论上拥有的所有功能。
 * 例如 Redisson 原生 RLock 支持可重入，但当前 iron-lock 每次 acquire 都生成新的 ownerToken，尚未暴露
 * 逻辑可重入语义，因此 Redisson Provider 的 reentrantSupported 仍应为 false。</p>
 */
public final class LockProviderCapabilities {

    /** 自动续期模式。 */
    private final LockAutoRenewMode autoRenewMode;

    /** 是否允许用户显式调用 LockHandle.renew()。 */
    private final boolean manualRenewSupported;

    /** 是否支持原生 fencing token。 */
    private final boolean fencingTokenSupported;

    /** 是否支持 Provider 原生等待/唤醒机制。 */
    private final boolean nativeWaitSupported;

    /** 是否已通过统一 API 暴露公平锁能力。 */
    private final boolean fairLockSupported;

    /** 是否已通过统一 API 暴露可重入能力。 */
    private final boolean reentrantSupported;

    private LockProviderCapabilities(Builder builder) {
        this.autoRenewMode = builder.autoRenewMode;
        this.manualRenewSupported = builder.manualRenewSupported;
        this.fencingTokenSupported = builder.fencingTokenSupported;
        this.nativeWaitSupported = builder.nativeWaitSupported;
        this.fairLockSupported = builder.fairLockSupported;
        this.reentrantSupported = builder.reentrantSupported;
    }

    public static Builder builder() {
        return new Builder();
    }

    public LockAutoRenewMode getAutoRenewMode() {
        return autoRenewMode;
    }

    public boolean isAutoRenewSupported() {
        return autoRenewMode != LockAutoRenewMode.UNSUPPORTED;
    }

    public boolean isManualRenewSupported() {
        return manualRenewSupported;
    }

    public boolean isFencingTokenSupported() {
        return fencingTokenSupported;
    }

    public boolean isNativeWaitSupported() {
        return nativeWaitSupported;
    }

    public boolean isFairLockSupported() {
        return fairLockSupported;
    }

    public boolean isReentrantSupported() {
        return reentrantSupported;
    }

    public static final class Builder {
        private LockAutoRenewMode autoRenewMode = LockAutoRenewMode.UNSUPPORTED;
        private boolean manualRenewSupported;
        private boolean fencingTokenSupported;
        private boolean nativeWaitSupported;
        private boolean fairLockSupported;
        private boolean reentrantSupported;

        private Builder() {}

        /**
         * 兼容原有测试/Provider 的便捷写法。
         * true 默认解释为 CORE_MANAGED；新 Provider 推荐直接使用 autoRenewMode(...)。
         */
        public Builder autoRenewSupported(boolean supported) {
            this.autoRenewMode = supported
                    ? LockAutoRenewMode.CORE_MANAGED
                    : LockAutoRenewMode.UNSUPPORTED;
            return this;
        }

        public Builder autoRenewMode(LockAutoRenewMode autoRenewMode) {
            this.autoRenewMode = autoRenewMode == null
                    ? LockAutoRenewMode.UNSUPPORTED
                    : autoRenewMode;
            return this;
        }

        public Builder manualRenewSupported(boolean manualRenewSupported) {
            this.manualRenewSupported = manualRenewSupported;
            return this;
        }

        public Builder fencingTokenSupported(boolean fencingTokenSupported) {
            this.fencingTokenSupported = fencingTokenSupported;
            return this;
        }

        public Builder nativeWaitSupported(boolean nativeWaitSupported) {
            this.nativeWaitSupported = nativeWaitSupported;
            return this;
        }

        public Builder fairLockSupported(boolean fairLockSupported) {
            this.fairLockSupported = fairLockSupported;
            return this;
        }

        public Builder reentrantSupported(boolean reentrantSupported) {
            this.reentrantSupported = reentrantSupported;
            return this;
        }

        public LockProviderCapabilities build() {
            return new LockProviderCapabilities(this);
        }
    }
}

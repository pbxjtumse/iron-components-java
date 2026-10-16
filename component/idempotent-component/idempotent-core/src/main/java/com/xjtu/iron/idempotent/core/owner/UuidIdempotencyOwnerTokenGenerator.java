package com.xjtu.iron.idempotent.core.owner;

import java.util.UUID;

/**
 * 基于 UUID 的默认 ownerToken 生成器。
 *
 * <p>namespace/key 不直接编码进 token；它们已经由 Repository 的唯一键确定逻辑记录。
 * UUID 只需要保证不同 acquisition generation 的 owner 标识足够唯一。</p>
 */
public final class UuidIdempotencyOwnerTokenGenerator
        implements IdempotencyOwnerTokenGenerator {

    @Override
    public String generate(String namespace, String key) {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

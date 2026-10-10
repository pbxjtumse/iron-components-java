package com.xjtu.iron.distributed.lock.spi.fencing;

/**
 * fencing token Provider。
 *
 * <p>该接口用于支持“锁 Provider”和“fencing token Provider”分离。例如 Redis 负责互斥抢锁，数据库 sequence
 * 负责生成更可靠的 fencing token。</p>
 */
public interface FencingTokenProvider {

    /**
     * Provider 名称。
     *
     * @return Provider 名称，例如 {@code redis}、{@code mybatis-sequence}。
     */
    String providerName();

    /**
     * 判断当前 Provider 是否支持指定锁的 fencing token 生成。
     *
     * @param request fencing token 请求。
     * @return 支持返回 true。
     */
    boolean supports(FencingTokenRequest request);

    /**
     * 生成下一个 fencing token。
     *
     * @param request fencing token 请求。
     * @return fencing token 响应。
     */
    FencingTokenResponse nextToken(FencingTokenRequest request);
}

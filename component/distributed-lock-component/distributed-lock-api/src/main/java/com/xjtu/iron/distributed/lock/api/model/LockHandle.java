package com.xjtu.iron.distributed.lock.api.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 锁句柄，表示“一次成功加锁后的租约凭证”。
 * 负责本次锁租约的续期、释放、检查、断言 ,注意和  DistributedLockClient 其作用  负责创建锁租约，提供模板执行入口 不同
 * <p>它不是锁定义本身，而是某次加锁成功后返回给业务方的持锁证明。业务方后续解锁、续期、失锁检查，
 * 都应该围绕本对象完成。</p>
 *
 * <p>重要语义：</p>
 * <ul>
 *     <li>{@code ownerToken}：本次锁租约的唯一归属凭证，用于安全解锁和安全续期。</li>
 *     <li>{@code fencingToken}：单调递增的业务写入版本号，用于防止旧 owner 恢复后覆盖新 owner 结果。</li>
 *     <li>{@code leaseTime}：本次锁的租约时间，超过该时间没有续期，锁会被底层存储自动释放。</li>
 * </ul>
 *
 * <p>本接口允许跨线程传递。例如线程 A 加锁成功后，把 LockHandle 传给 CompletableFuture 回调线程 B，
 * B 仍然可以使用同一个 ownerToken 安全解锁。组件不应该用 Java Thread 作为分布式锁 owner。</p>
 */
public interface LockHandle extends AutoCloseable {

    /**
     * 业务锁名称。
     *
     * @return 业务方传入的逻辑名称，例如 {@code settle:batch:20260708}。
     */
    String lockName();

    /**
     * 底层 Provider 使用的真实锁 key 或路径。
     *
     * <p>Redis Provider 中可能是 Redis key；ZK Provider 中可能是 ZNode path；Etcd Provider 中可能是 KV key。
     * API 层不要求业务理解该字段，主要用于日志、排查和指标。</p>
     *
     * @return Provider 真实锁标识。
     */
    String lockKey();

    /**
     * 本次锁租约的唯一 owner token。
     *
     * <p>ownerToken 是“这次加锁成功属于我”的证明。Redis Provider 中，lockKey 的 value 通常就是这个 token。
     * 解锁和续期时必须比较底层 value 是否仍然等于本 token，避免旧 owner 误删或误续新 owner 的锁。</p>
     *
     * <p>ownerToken 只能保护锁记录本身，不能保护业务数据库写入。业务写入保护应使用
     * {@link #fencingToken()}。</p>
     *
     * @return 本次锁租约的唯一 owner token。
     */
    String ownerToken();

    /**
     * 本次锁租约的 fencing token。
     *
     * <p>fencing token 是单调递增版本号。每次成功获取同一把锁时，新的 fencing token 应大于旧 token。
     * 业务写入 DB 时应带上该 token，并通过条件更新拒绝旧 token，例如：</p>
     *
     * <pre>{@code
     * update settle_batch
     * set status = 'PROCESSING', fencing_token = :token
     * where batch_no = :batchNo
     *   and fencing_token < :token;
     * }</pre>
     *
     * <p>如果 Provider 或当前选项未启用 fencing，本方法返回 {@link OptionalLong#empty()}。</p>
     *
     * @return fencing token，可能为空。
     */
    OptionalLong fencingToken();

    /**
     * fencing token 的来源 Provider。
     *
     * <p>Redis 原生 INCR 模式通常返回 {@code redis}；独立 DB sequence 模式通常返回
     * {@code mybatis-sequence}。未启用 fencing 时为空。</p>
     */
    default Optional<String> fencingTokenProviderName() {
        return Optional.empty();
    }

    /**
     * 加锁成功时间。
     *
     * @return 加锁成功的本地时间。
     */
    Instant acquiredAt();

    /**
     * 本次锁租约时间。
     *
     * @return 租约时长。
     */
    Duration leaseTime();

    /**
     * 本地估算的租约过期时间。
     *
     * <p>这是客户端根据 acquiredAt 和 leaseTime 估算出来的时间，不等同于 Redis/ZK/Etcd 服务端的绝对事实，
     * 只能用于日志、指标和续期调度参考。</p>
     *
     * @return 本地估算的租约过期时间。
     */
    Instant expireAt();

    /**
     * 当前 handle 是否已经被组件判定为失锁。
     *
     * <p>续期失败、主动检查失败、解锁时发现 ownerToken 不匹配，都可能把本 handle 标记为 lost。
     * 该状态是客户端侧状态，不能替代业务 DB 的 fencing 校验。</p>
     *
     * @return 已判定失锁返回 {@code true}。
     */
    boolean isLost();

    /**
     * 当前 handle 是否已经进入过本地释放流程。
     *
     * <p>注意：本方法不表示底层锁一定释放成功，只表示当前 LockHandle 的 release 流程已经被尝试过，
     * 主要用于避免同一个 handle 重复执行远程释放逻辑。底层释放是否成功需要结合 release 结果、事件或
     * execute 返回的 LockResult 判断。</p>
     *
     * @return 已进入过本地释放流程返回 {@code true}。
     */
    boolean isReleaseAttempted();

    /**
     * 当前时刻是否仍然持有锁。
     *
     * <p>该方法通常会访问底层 Provider 校验 ownerToken。它只代表检查这一刻的结果，不代表后续业务执行期间
     * 锁不会过期或丢失。</p>
     *
     * @return 当前时刻仍持有锁返回 {@code true}。
     */
    boolean checkHeld();

    /**
     * 使用原 leaseTime 安全续期。
     *
     * @return 续期成功返回 {@code true}，失锁或续期失败返回 {@code false}。
     */
    boolean renew();

    /**
     * 安全释放锁。
     *
     * <p>释放必须校验 ownerToken。释放失败不应该影响业务异常继续向外传递，但应记录事件和指标。</p>
     *
     * @return 释放成功返回 {@code true}，锁已丢失或已释放返回 {@code false}。
     */
    boolean unlock();

    /**
     * 断言当前 handle 仍然持有锁。
     *
     * <p>若锁已丢失，应抛出 {@code LockLostException} 或其子类。长流程业务可以在关键写入前调用该方法
     * 提前发现失锁；但最终仍需要 fencing token + DB 条件更新兜底。</p>
     */
    void assertHeld();

    /**
     * 兼容 try-with-resources，默认执行安全释放。
     */
    @Override
    default void close() {
        unlock();
    }
}

package com.xjtu.iron.distributed.lock.provider.redisson;

import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Redisson Provider 契约集成测试。
 *
 * <p>Docker 可用时验证：互斥、owner 安全释放、跨 Java 线程释放、原生 fencing 单调递增、
 * provider-native Pub/Sub waiting。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class RedissonLockProviderIntegrationTest {

//    @Container
//    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
//            .withExposedPorts(6379);
//
//    static RedissonClient client;
//    static RedissonLockProvider provider;
//
//    @BeforeAll
//    static void setUp() {
//        Config config = new Config();
//        config.setLockWatchdogTimeout(2_000L);
//        config.useSingleServer().setAddress(
//                "redis://" + REDIS.getHost() + ':' + REDIS.getMappedPort(6379));
//        client = Redisson.create(config);
//        provider = new RedissonLockProvider(client, new RedissonLockKeyBuilder("test:iron:lock:redisson"), new RedissonOwnershipRegistry(),
//                Duration.ofSeconds(2));
//    }
//
//    @AfterAll
//    static void tearDown() {
//        if (client != null) {
//            client.shutdown();
//        }
//    }
//
//    @Test
//    void shouldMutuallyExcludeAndReleaseByOwnerTokenAcrossJavaThreads() throws Exception {
//        LockAcquireResponse first = provider.acquire(request("cross-thread", "owner-a", false, LockWaitStrategy.NO_WAIT));
//        assertTrue(first.isAcquired());
//
//        LockAcquireResponse second = provider.acquire(request("cross-thread", "owner-b", false, LockWaitStrategy.NO_WAIT));
//        assertTrue(second.isNotAcquired());
//
//        LockLease lease = first.getLease();
//        CompletableFuture<Boolean> released = CompletableFuture.supplyAsync(() ->
//                provider.release(LockReleaseRequest.fromLease(lease)).isReleased());
//        assertTrue(released.get(3, TimeUnit.SECONDS));
//    }
//
//
//    @Test
//    void wrongOwnerMustNotReleaseCurrentLock() {
//        LockAcquireResponse first = provider.acquire(request("wrong-owner", "owner-a", false, LockWaitStrategy.NO_WAIT));
//        assertTrue(first.isAcquired());
//
//        LockLease fakeLease = LockLease.builder()
//                .providerName("redisson")
//                .namespace(first.getLease().getNamespace())
//                .lockName(first.getLease().getLockName())
//                .lockKey(first.getLease().getLockKey())
//                .ownerToken("owner-b")
//                .leaseTime(first.getLease().getLeaseTime())
//                .acquiredAt(first.getLease().getAcquiredAt())
//                .expireAt(first.getLease().getExpireAt())
//                .build();
//
//        assertFalse(provider.release(LockReleaseRequest.fromLease(fakeLease)).isReleased());
//        assertTrue(provider.check(LockCheckRequest.fromLease(first.getLease())).isHeld());
//        assertTrue(provider.release(LockReleaseRequest.fromLease(first.getLease())).isReleased());
//    }
//
//    @Test
//    void expiredOldOwnerMustNotReleaseNewOwner() throws Exception {
//        LockOptions shortLease = LockOptions.builder()
//                .namespace("test")
//                .leaseTime(Duration.ofMillis(300))
//                .waitTime(Duration.ZERO)
//                .build();
//        LockAcquireResponse old = provider.acquire(LockAcquireRequest.builder()
//                .lockName("stale-release")
//                .ownerToken("old-owner")
//                .options(shortLease)
//                .nativeFencingRequired(false)
//                .build());
//        assertTrue(old.isAcquired());
//        Thread.sleep(500L);
//
//        LockAcquireResponse fresh = provider.acquire(LockAcquireRequest.builder()
//                .lockName("stale-release")
//                .ownerToken("fresh-owner")
//                .options(shortLease)
//                .nativeFencingRequired(false)
//                .build());
//        assertTrue(fresh.isAcquired());
//
//        assertFalse(provider.release(LockReleaseRequest.fromLease(old.getLease())).isReleased());
//        assertTrue(provider.check(LockCheckRequest.fromLease(fresh.getLease())).isHeld());
//        provider.release(LockReleaseRequest.fromLease(fresh.getLease()));
//    }
//
//
//    @Test
//    void sameJavaThreadMustNotLeakRedissonReentrantSemantics() {
//        LockAcquireResponse first = provider.acquire(request("no-reentrant", "owner-a", false, LockWaitStrategy.NO_WAIT));
//        assertTrue(first.isAcquired());
//
//        // 同一 JUnit 线程再次申请同一个 lockKey。Redisson RLock 原生是 reentrant，
//        // 但 iron-lock 当前没有暴露 reentrant 语义，因此第二次必须表现为 NOT_ACQUIRED。
//        LockAcquireResponse second = provider.acquire(request("no-reentrant", "owner-b", false, LockWaitStrategy.NO_WAIT));
//        assertTrue(second.isNotAcquired());
//
//        assertTrue(provider.release(LockReleaseRequest.fromLease(first.getLease())).isReleased());
//    }
//
//    @Test
//    void shouldReturnIncreasingNativeFencingTokens() {
//        LockAcquireResponse first = provider.acquire(request("fenced", "owner-1", true, LockWaitStrategy.NO_WAIT));
//        long token1 = first.getLease().fencingToken().orElseThrow();
//        assertTrue(provider.release(LockReleaseRequest.fromLease(first.getLease())).isReleased());
//
//        LockAcquireResponse second = provider.acquire(request("fenced", "owner-2", true, LockWaitStrategy.NO_WAIT));
//        long token2 = second.getLease().fencingToken().orElseThrow();
//        assertTrue(token2 > token1);
//        provider.release(LockReleaseRequest.fromLease(second.getLease()));
//    }
//
//    @Test
//    void shouldUseProviderNativePubSubWaiting() throws Exception {
//        LockAcquireResponse first = provider.acquire(request("pubsub", "owner-a", false, LockWaitStrategy.NO_WAIT));
//        assertTrue(first.isAcquired());
//
//        CompletableFuture<LockAcquireResponse> waiting = CompletableFuture.supplyAsync(() ->
//                provider.acquire(request("pubsub", "owner-b", false, LockWaitStrategy.PROVIDER_NATIVE)));
//
//        Thread.sleep(200L);
//        provider.release(LockReleaseRequest.fromLease(first.getLease()));
//
//        LockAcquireResponse second = waiting.get(3, TimeUnit.SECONDS);
//        assertTrue(second.isAcquired());
//        provider.release(LockReleaseRequest.fromLease(second.getLease()));
//    }
//
//    @Test
//    void providerManagedWatchdogShouldKeepLockAlivePastInitialTimeout() throws Exception {
//        LockOptions options = LockOptions.builder()
//                .namespace("test")
//                .leaseTime(Duration.ofSeconds(2))
//                .autoRenew(true)
//                .waitTime(Duration.ZERO)
//                .build();
//        LockAcquireRequest request = LockAcquireRequest.builder()
//                .lockName("watchdog")
//                .ownerToken("owner-watchdog")
//                .options(options)
//                .nativeFencingRequired(false)
//                .build();
//        LockAcquireResponse response = provider.acquire(request);
//        assertTrue(response.isAcquired());
//        Thread.sleep(2_800L);
//        assertTrue(provider.check(LockCheckRequest.fromLease(response.getLease())).isHeld());
//        provider.release(LockReleaseRequest.fromLease(response.getLease()));
//    }
//
//    private static LockAcquireRequest request(String lockName, String ownerToken, boolean nativeFencing, LockWaitStrategy waitStrategy) {
//        LockOptions options = LockOptions.builder()
//                .namespace("test")
//                .leaseTime(Duration.ofSeconds(2))
//                .waitTime(waitStrategy == LockWaitStrategy.PROVIDER_NATIVE ? Duration.ofSeconds(2) : Duration.ZERO)
//                .waitStrategy(waitStrategy)
//                .build();
//        return LockAcquireRequest.builder()
//                .lockName(lockName)
//                .ownerToken(ownerToken)
//                .options(options)
//                .nativeFencingRequired(nativeFencing)
//                .build();
//    }
}

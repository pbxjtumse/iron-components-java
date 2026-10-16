package com.xjtu.iron.distributed.lock.starter.autoconfigure;

import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.provider.redis.RedisLockProvider;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckStatus;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseStatus;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewStatus;
import com.xjtu.iron.distributed.lock.starter.redis.StringRedisTemplateRedisLockScriptExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis Lua + Spring Data Redis 适配层集成测试。
 *
 * <p>
 * Spring Data Redis 适配器位于 starter 模块，因此真实 Redis 集成测试也放在 starter 测试中，
 * provider-redis 模块只测试纯 Provider 语义和 Lua 返回值映射。
 * </p>
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisLockProviderIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;

    private static RedisLockProvider provider;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        provider = new RedisLockProvider(new StringRedisTemplateRedisLockScriptExecutor(redisTemplate));
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void nativeRedisFencingTokenShouldIncreaseForEachSuccessfulLease() {
        String lockName = "integration:fencing:1";
        LockOptions options = LockOptions.builder()
                .leaseTime(Duration.ofSeconds(5))
                .fencingRequired(true)
                .build();

        LockAcquireResponse first = provider.acquire(LockAcquireRequest.builder()
                .lockName(lockName).ownerToken("owner-1").options(options)
                .nativeFencingRequired(true).build());
        assertTrue(first.isAcquired());
        long firstToken = first.getLease().fencingToken().orElseThrow();
        assertEquals(LockReleaseStatus.RELEASED, provider.release(LockReleaseRequest.fromLease(first.getLease())).getStatus());

        LockAcquireResponse second = provider.acquire(LockAcquireRequest.builder()
                .lockName(lockName).ownerToken("owner-2").options(options)
                .nativeFencingRequired(true).build());
        assertTrue(second.isAcquired());
        long secondToken = second.getLease().fencingToken().orElseThrow();
        assertTrue(secondToken > firstToken);
        provider.release(LockReleaseRequest.fromLease(second.getLease()));
    }

    @Test
    void acquireRenewCheckAndReleaseShouldUseLuaAtomically() {
        LockAcquireRequest acquireRequest = LockAcquireRequest.builder()
                .lockName("integration:job:1")
                .ownerToken("owner-a")
                .options(LockOptions.noWait(Duration.ofSeconds(5)))
                .build();

        LockAcquireResponse acquired = provider.acquire(acquireRequest);
        assertTrue(acquired.isAcquired());
        assertNotNull(acquired.getLease());

        LockAcquireResponse busy = provider.acquire(LockAcquireRequest.builder()
                .lockName("integration:job:1")
                .ownerToken("owner-b")
                .options(LockOptions.noWait(Duration.ofSeconds(5)))
                .build());
        assertTrue(busy.isNotAcquired());
        assertNotNull(busy.getRemainingTtl());

        assertEquals(LockCheckStatus.HELD, provider.check(LockCheckRequest.fromLease(acquired.getLease())).getStatus());
        assertEquals(LockRenewStatus.RENEWED, provider.renew(LockRenewRequest.fromLease(acquired.getLease())).getStatus());
        assertEquals(LockReleaseStatus.RELEASED, provider.release(LockReleaseRequest.fromLease(acquired.getLease())).getStatus());
        assertEquals(LockCheckStatus.NOT_FOUND, provider.check(LockCheckRequest.fromLease(acquired.getLease())).getStatus());
    }
}

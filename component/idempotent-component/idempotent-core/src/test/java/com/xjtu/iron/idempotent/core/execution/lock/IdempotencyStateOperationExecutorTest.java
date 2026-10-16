package com.xjtu.iron.idempotent.core.execution.lock;

import com.xjtu.iron.idempotent.api.policy.IdempotencyLockOptions;
import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRepository;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;
import com.xjtu.iron.idempotent.core.observation.IdempotencyEvent;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyStateOperationExecutorTest {
    private final List<IdempotencyEvent> events = new ArrayList<>();
    private final IdempotencyStateOperationExecutor operations = new IdempotencyStateOperationExecutor(null, events::add, Clock.systemUTC());
    private final IdempotencyRepository repository = (IdempotencyRepository) Proxy.newProxyInstance(
            IdempotencyRepository.class.getClassLoader(), new Class<?>[]{IdempotencyRepository.class}, (proxy, method, args) -> {
                if (method.getName().equals("providerName")) return "test";
                throw new AssertionError("unexpected Repository call: " + method.getName());
            });

    @Test
    void disabledLockExecutesStateOperationOnce() {
        AtomicInteger calls = new AtomicInteger();
        var outcome = invoke(IdempotencyLockOptions.disabled(), calls);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(outcome.result()).isEqualTo(1);
        assertThat(outcome.lockFallback()).isFalse();
        assertThat(outcome.lockRejected()).isFalse();
        assertThat(events).isEmpty();
    }

    @Test
    void missingLockClientFallsBackOnlyWhenAllowed() {
        AtomicInteger calls = new AtomicInteger();
        var outcome = invoke(IdempotencyLockOptions.builder().enabled(true).fallbackToStateOnFailure(true).build(), calls);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(outcome.result()).isEqualTo(1);
        assertThat(outcome.lockFallback()).isTrue();
        assertThat(outcome.lockRejected()).isFalse();
        assertThat(events).hasSize(1);
    }

    @Test
    void missingLockClientWithFallbackDisabledNeverCallsRepository() {
        AtomicInteger calls = new AtomicInteger();
        var outcome = invoke(IdempotencyLockOptions.builder().enabled(true).fallbackToStateOnFailure(false).build(), calls);
        assertThat(calls.get()).isZero();
        assertThat(outcome.result()).isNull();
        assertThat(outcome.lockRejected()).isTrue();
        assertThat(outcome.error()).isInstanceOf(IllegalStateException.class);
        assertThat(events).isEmpty();
    }

    private StateOperationOutcome<Integer> invoke(IdempotencyLockOptions lock, AtomicInteger calls) {
        var policy = IdempotencyPolicy.builder().lockOptions(lock).build();
        return operations.invoke(policy, repository, IdempotencyStorageContext.of("orders", 0), "merchant", "key", calls::incrementAndGet);
    }
}

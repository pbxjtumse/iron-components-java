package com.xjtu.iron.concurrency.api.execution.pool;

import com.xjtu.iron.concurrency.api.enums.QueueType;
import com.xjtu.iron.concurrency.api.enums.RejectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ThreadPoolSpec 参数契约测试")
class ThreadPoolSpecValidationTest {

    @Test
    @DisplayName("validate：合法配置应补齐默认线程名前缀")
    void validate_should_fill_default_thread_name_prefix() {
        ThreadPoolSpec spec = new ThreadPoolSpec();
        spec.setName("io");
        spec.setCorePoolSize(2);
        spec.setMaxPoolSize(4);
        spec.setQueueCapacity(100);

        spec.validate();

        assertThat(spec.getThreadNamePrefix()).isEqualTo("xjtu-iron-io-");
    }

    @Test
    @DisplayName("name 为空时应拒绝")
    void should_reject_blank_name() {
        ThreadPoolSpec spec = new ThreadPoolSpec();
        spec.setName(" ");

        assertThatThrownBy(spec::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("maxPoolSize 小于 corePoolSize 时应拒绝")
    void should_reject_max_less_than_core() {
        ThreadPoolSpec spec = new ThreadPoolSpec();
        spec.setName("bad");
        spec.setCorePoolSize(4);
        spec.setMaxPoolSize(2);

        assertThatThrownBy(spec::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPoolSize");
    }

    @Test
    @DisplayName("普通有界队列 queueCapacity <= 0 时应拒绝")
    void should_reject_non_positive_queue_capacity_for_bounded_queue() {
        ThreadPoolSpec spec = new ThreadPoolSpec();
        spec.setName("bad-queue");
        spec.setQueueType(QueueType.BOUNDED_LINKED_BLOCKING_QUEUE);
        spec.setQueueCapacity(0);

        assertThatThrownBy(spec::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queueCapacity");
    }

    @Test
    @DisplayName("DIRECT_HANDOFF 允许 queueCapacity=0")
    void direct_handoff_should_allow_zero_queue_capacity() {
        ThreadPoolSpec spec = new ThreadPoolSpec();
        spec.setName("direct");
        spec.setQueueType(QueueType.DIRECT_HANDOFF);
        spec.setQueueCapacity(0);

        spec.validate();

        assertThat(spec.getQueueType()).isEqualTo(QueueType.DIRECT_HANDOFF);
    }

    @Test
    @DisplayName("BLOCKING_WAIT 策略下 rejectionWaitTime 必须大于 0")
    void blocking_wait_should_require_positive_wait_time() {
        ThreadPoolSpec spec = new ThreadPoolSpec();
        spec.setName("blocking");
        spec.setRejectionPolicy(RejectionPolicy.BLOCKING_WAIT);
        spec.setRejectionWaitTime(Duration.ZERO);

        assertThatThrownBy(spec::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejectionWaitTime");
    }
}

package com.xjtu.iron.concurrency.api.execution.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AsyncTask 参数契约测试")
class AsyncTaskValidationTest {

    @Test
    @DisplayName("validate：未传 taskId 时应自动生成，且 metadata 固化基础字段")
    void validate_should_generate_task_id_and_build_metadata() {
        AsyncTask<String> task = AsyncTask.of("default", "query-user", () -> "OK")
                .bizKey("userId=1001")
                .description("query user profile")
                .tag("scene", "profile")
                .tag("blank", null)
                .tag("", "ignored");

        task.validate();

        assertThat(task.getTaskId()).isNotBlank();
        assertThat(task.metadata().getTaskId()).isEqualTo(task.getTaskId());
        assertThat(task.metadata().getExecutorName()).isEqualTo("default");
        assertThat(task.metadata().getTaskName()).isEqualTo("query-user");
        assertThat(task.metadata().getBizKey()).isEqualTo("userId=1001");
        assertThat(task.metadata().getTags()).containsEntry("scene", "profile");
        assertThat(task.metadata().getTags()).doesNotContainKeys("blank", "");
    }

    @Test
    @DisplayName("tags：应进行防御性复制，外部 Map 修改不应污染任务标签")
    void tags_should_be_defensively_copied() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("source", "unit-test");

        AsyncTask<String> task = AsyncTask.of("default", "copy-tags", () -> "OK")
                .tags(tags);

        tags.put("source", "changed");
        tags.put("new", "value");

        assertThat(task.getTags()).containsEntry("source", "unit-test");
        assertThat(task.getTags()).doesNotContainKey("new");
        assertThatThrownBy(() -> task.getTags().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Nested
    @DisplayName("必填字段校验")
    class RequiredFields {

        @Test
        @DisplayName("executorName 为空时应拒绝")
        void should_reject_blank_executor_name() {
            AsyncTask<String> task = AsyncTask.of(" ", "task", () -> "OK");

            assertThatThrownBy(task::validate)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("executorName");
        }

        @Test
        @DisplayName("taskName 为空时应拒绝")
        void should_reject_blank_task_name() {
            AsyncTask<String> task = AsyncTask.of("default", " ", () -> "OK");

            assertThatThrownBy(task::validate)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("taskName");
        }

        @Test
        @DisplayName("operation 为 null 时应拒绝")
        void should_reject_null_operation() {
            AsyncTask<String> task = AsyncTask.of("default", "task", null);

            assertThatThrownBy(task::validate)
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("operation");
        }
    }

    @Nested
    @DisplayName("超时参数校验")
    class TimeoutValidation {

        @Test
        @DisplayName("timeout 为 0 时应拒绝")
        void should_reject_zero_timeout() {
            AsyncTask<String> task = AsyncTask.of("default", "task", () -> "OK")
                    .timeout(Duration.ZERO);

            assertThatThrownBy(task::validate)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("timeout");
        }

        @Test
        @DisplayName("timeout 为负数时应拒绝")
        void should_reject_negative_timeout() {
            AsyncTask<String> task = AsyncTask.of("default", "task", () -> "OK")
                    .timeout(Duration.ofMillis(-1));

            assertThatThrownBy(task::validate)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("timeout");
        }

        @Test
        @DisplayName("queueTimeout 为 0 时应拒绝")
        void should_reject_zero_queue_timeout() {
            AsyncTask<String> task = AsyncTask.of("default", "task", () -> "OK")
                    .queueTimeout(Duration.ZERO);

            assertThatThrownBy(task::validate)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("queueTimeout");
        }

        @Test
        @DisplayName("interruptOnCancel=true 但 cancelOnTimeout=false 时应拒绝")
        void should_reject_interrupt_without_cancel_on_timeout() {
            AsyncTask<String> task = AsyncTask.of("default", "task", () -> "OK")
                    .cancelOnTimeout(false)
                    .interruptOnCancel(true);

            assertThatThrownBy(task::validate)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cancelOnTimeout");
        }
    }
}

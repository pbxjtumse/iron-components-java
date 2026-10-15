package com.xjtu.iron.relational.mybatis.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证动态表名只允许安全的标识符形式。 */
class MyBatisTableNameValidatorTest {

    @Test
    void shouldAcceptPlainAndSchemaQualifiedTableNames() {
        assertThat(MyBatisTableNameValidator.requireValid("task_01", "default_task")).isEqualTo("task_01");
        assertThat(MyBatisTableNameValidator.requireValid("tenant_01.task_02", "default_task")).isEqualTo("tenant_01.task_02");
        assertThat(MyBatisTableNameValidator.requireValid(null, "default_task")).isEqualTo("default_task");
    }

    @Test
    void shouldRejectSqlFragmentsAndMultiLevelNames() {
        assertThatThrownBy(() -> MyBatisTableNameValidator.requireValid("task; DROP TABLE task", "default_task")).isInstanceOf(
            IllegalArgumentException.class);
        assertThatThrownBy(() -> MyBatisTableNameValidator.requireValid("catalog.schema.task", "default_task")).isInstanceOf(
            IllegalArgumentException.class);
    }
}

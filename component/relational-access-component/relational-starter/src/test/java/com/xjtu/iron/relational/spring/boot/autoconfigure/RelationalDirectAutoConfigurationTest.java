package com.xjtu.iron.relational.spring.boot.autoconfigure;

import com.xjtu.iron.relational.mybatis.access.MyBatisAccess;
import com.xjtu.iron.relational.mybatis.observation.MyBatisAccessInvocation;
import com.xjtu.iron.relational.mybatis.observation.MyBatisAccessListener;
import com.xjtu.iron.relational.mybatis.resource.DirectMyBatisResourceRegistry;
import com.xjtu.iron.relational.mybatis.resource.DirectMyBatisSqlSessionFactoryCustomizer;
import com.xjtu.iron.relational.mybatis.routing.MyBatisAccessResolver;
import com.xjtu.iron.transaction.api.execution.TransactionExecutorResolver;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 不加载幂等组件，验证公共 Direct 目录、配置定制与访问观测。 */
class RelationalDirectAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withConfiguration(
        AutoConfigurations.of(MybatisAutoConfiguration.class, RelationalDirectAutoConfiguration.class,
            RelationalAccessAutoConfiguration.class)).withPropertyValues("xjtu.iron.relational.direct.enabled=true");

    @Test
    void shouldRouteAcrossDatabasesWithCustomMappersPluginsAndListeners() {
        List<String> customized = new ArrayList<>();
        List<String> observed = new ArrayList<>();
        AtomicInteger queries = new AtomicInteger();
        MyBatisAccessListener listener = new MyBatisAccessListener() {
            @Override
            public void afterSuccess(MyBatisAccessInvocation invocation, Duration elapsed) {
                observed.add(invocation.getOperationName());
            }
        };
        runner.withBean("first", DataSource.class, () -> dataSource("first"))
            .withBean("second", DataSource.class, () -> dataSource("second"))
            .withBean(MyBatisAccessListener.class, () -> listener)
            .withBean(DirectMyBatisSqlSessionFactoryCustomizer.class, () -> (key, factory) -> {
                customized.add(key);
                Configuration configuration = new Configuration();
                configuration.addMapper(ProbeMapper.class);
                factory.setConfiguration(configuration);
                factory.setPlugins(new QueryCounter(queries));
            }).run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(DirectMyBatisResourceRegistry.class)
                    .hasSingleBean(MyBatisAccessResolver.class).hasSingleBean(TransactionExecutorResolver.class);
                assertThat(context).doesNotHaveBean(MyBatisAccess.class);
                var resources = context.getBean(DirectMyBatisResourceRegistry.class);
                var accesses = context.getBean(MyBatisAccessResolver.class);
                var transactions = context.getBean(TransactionExecutorResolver.class);
                assertThat(accesses).isSameAs(resources.getMyBatisAccessResolver());
                assertThat(transactions).isSameAs(resources.getTransactionExecutorResolver());
                assertThat(accesses.resolve("first").execute("probe.first", ProbeMapper.class, ProbeMapper::marker)).isEqualTo("first");
                assertThat(accesses.resolve("second").execute("probe.second", ProbeMapper.class, ProbeMapper::marker)).isEqualTo("second");
                assertThat(customized).containsExactlyInAnyOrder("first", "second");
                assertThat(observed).containsExactly("probe.first", "probe.second");
                assertThat(queries).hasValue(2);
            });
    }

    @Test
    void shouldReuseUserResourceRegistry() {
        var custom = DirectMyBatisResourceRegistry.fromDataSources(Map.of("custom", dataSource("custom")));
        runner.withBean(DirectMyBatisResourceRegistry.class, () -> custom).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(DirectMyBatisResourceRegistry.class);
            assertThat(context.getBean(DirectMyBatisResourceRegistry.class)).isSameAs(custom);
            assertThat(context.getBean(MyBatisAccessResolver.class)).isSameAs(custom.getMyBatisAccessResolver());
        });
    }

    @Test
    void shouldAllowDisablingDirectResources() {
        runner.withPropertyValues("xjtu.iron.relational.direct.enabled=false")
            .withBean("first", DataSource.class, () -> dataSource("first"))
            .withBean("second", DataSource.class, () -> dataSource("second"))
            .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(DirectMyBatisResourceRegistry.class)
                .doesNotHaveBean(MyBatisAccessResolver.class));
    }

    @Test
    void componentScanningShouldNotEnableImportOnlyDirectResources() {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);

        assertThat(scanner.findCandidateComponents(RelationalDirectResourceConfiguration.class.getPackageName()))
            .noneMatch(candidate -> RelationalDirectResourceConfiguration.class.getName().equals(candidate.getBeanClassName()));
    }

    @Test
    void shouldRejectCustomizerThatReplacesTheBoundDataSource() {
        runner.withBean(DataSource.class, () -> dataSource("original"))
            .withBean(DirectMyBatisSqlSessionFactoryCustomizer.class, () -> (key, factory) -> factory.setDataSource(dataSource("other")))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("same DataSource");
            });
    }

    private static DataSource dataSource(String marker) {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:direct_starter_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE direct_probe (id INT PRIMARY KEY, marker VARCHAR(64))");
        jdbc.update("INSERT INTO direct_probe VALUES (1, ?)", marker);
        return source;
    }

    public interface ProbeMapper {
        @Select("SELECT marker FROM direct_probe WHERE id = 1")
        String marker();
    }

    @Intercepts(@Signature(type = Executor.class, method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
    private static final class QueryCounter implements Interceptor {
        private final AtomicInteger queries;

        private QueryCounter(AtomicInteger queries) {
            this.queries = queries;
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            queries.incrementAndGet();
            return invocation.proceed();
        }
    }
}

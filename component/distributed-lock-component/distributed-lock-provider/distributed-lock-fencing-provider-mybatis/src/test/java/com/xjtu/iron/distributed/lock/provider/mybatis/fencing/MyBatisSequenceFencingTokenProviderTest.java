package com.xjtu.iron.distributed.lock.provider.mybatis.fencing;

import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenRequest;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse;
import com.xjtu.iron.relational.mybatis.MyBatisAccessListener;
import com.xjtu.iron.relational.mybatis.SpringMyBatisAccess;
import com.xjtu.iron.transaction.core.executor.DefaultTransactionExecutor;
import com.xjtu.iron.transaction.provider.spring.transaction.SpringTransactionProvider;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 MyBatis fencing token Provider 的递增性和并发唯一性。 */
class MyBatisSequenceFencingTokenProviderTest {

    /** 被测 fencing token Provider。 */
    private MyBatisSequenceFencingTokenProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:fencing_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000");
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE iron_lock_fencing_token ("
                    + "namespace VARCHAR(128) NOT NULL,"
                    + "lock_name VARCHAR(512) NOT NULL,"
                    + "current_token BIGINT NOT NULL,"
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY(namespace, lock_name))");
        }

        Configuration configuration = new Configuration();
        configuration.addMapper(FencingTokenMapper.class);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setConfiguration(configuration);
        SqlSessionFactory factory = factoryBean.getObject();
        SqlSessionTemplate template = new SqlSessionTemplate(factory);
        DefaultTransactionExecutor transactions = new DefaultTransactionExecutor(
                new SpringTransactionProvider(new DataSourceTransactionManager(dataSource))
        );
        provider = new MyBatisSequenceFencingTokenProvider(new SpringMyBatisAccess(
                dataSource,
                template,
                transactions,
                MyBatisAccessListener.noop()
        ));
    }

    @Test
    void shouldIssueIncreasingTokensForSameLock() {
        long first = provider.nextToken(request("order:1")).token().orElseThrow();
        long second = provider.nextToken(request("order:1")).token().orElseThrow();
        long anotherLock = provider.nextToken(request("order:2")).token().orElseThrow();

        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);
        assertThat(anotherLock).isEqualTo(1L);
    }

    @Test
    void concurrentIssuanceShouldBeUniqueAndMonotonic() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Long>> tasks = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                tasks.add(() -> {
                    FencingTokenResponse response = provider.nextToken(request("concurrent"));
                    assertThat(response.isIssued()).isTrue();
                    return response.token().orElseThrow();
                });
            }
            List<Future<Long>> futures = executor.invokeAll(tasks);
            List<Long> tokens = new ArrayList<>();
            for (Future<Long> future : futures) {
                tokens.add(future.get());
            }
            assertThat(tokens).doesNotHaveDuplicates();
            assertThat(tokens).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.LongStream.rangeClosed(1, 20).boxed().toList()
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private FencingTokenRequest request(String lockName) {
        return FencingTokenRequest.builder()
                .namespace("test")
                .lockName(lockName)
                .ownerToken("owner")
                .options(LockOptions.builder().fencingRequired(true).build())
                .build();
    }
}

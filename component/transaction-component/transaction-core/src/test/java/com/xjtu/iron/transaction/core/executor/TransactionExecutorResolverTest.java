package com.xjtu.iron.transaction.core.executor;

import com.xjtu.iron.transaction.api.definition.TransactionOptions;
import com.xjtu.iron.transaction.api.execution.TransactionCallback;
import com.xjtu.iron.transaction.api.execution.TransactionExecutor;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TransactionExecutorResolverTest {
    private final TransactionExecutor executor = new TransactionExecutor() {
        @Override public <T> T execute(TransactionOptions options, TransactionCallback<T> callback) { return callback.execute(null); }
    };

    @Test
    void resolvesExactResourceWithoutFallbackAndCopiesInput() {
        Map<String, TransactionExecutor> input = new LinkedHashMap<>(Map.of("db_00", executor));
        var resolver = new RoutingTransactionExecutorResolver(input);
        input.clear();
        assertSame(executor, resolver.resolve(" db_00 "));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("db_01"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(" "));
        assertThrows(IllegalArgumentException.class, () -> new RoutingTransactionExecutorResolver(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new RoutingTransactionExecutorResolver(Map.of("db_00", executor, " db_00 ", executor)));
    }

    @Test
    void fixedResolverStillValidatesResourceIdentity() {
        var resolver = new FixedTransactionExecutorResolver("db_00", executor);
        assertSame(executor, resolver.resolve("db_00"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("db_09"));
    }
}

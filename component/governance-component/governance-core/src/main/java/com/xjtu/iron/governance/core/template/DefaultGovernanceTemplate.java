package com.xjtu.iron.governance.core.template;


import com.xjtu.iron.governance.api.context.GovernanceContext;
import com.xjtu.iron.governance.api.template.GovernanceTemplate;
import com.xjtu.iron.governance.core.executor.GovernanceExecutor;
import com.xjtu.iron.governance.model.resource.GovernanceResourceType;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DefaultGovernanceTemplate implements GovernanceTemplate {

    private final GovernanceExecutor governanceExecutor;

    public DefaultGovernanceTemplate(GovernanceExecutor governanceExecutor) {
        this.governanceExecutor = governanceExecutor;
    }

    @Override
    public <T> T execute(String resourceName, Supplier<T> supplier) {
        GovernanceContext context = createContext(resourceName);
        return governanceExecutor.execute(context, ctx -> supplier.get());
    }

    @Override
    public <T> T execute(String resourceName,
                         Supplier<T> supplier,
                         Function<Throwable, T> fallback) {
        GovernanceContext context = createContext(resourceName);
        return governanceExecutor.execute(
                context,
                ctx -> supplier.get(),
                (ctx, throwable) -> fallback.apply(throwable)
        );
    }

    @Override
    public void execute(String resourceName, Runnable runnable) {
        execute(resourceName, () -> {
            runnable.run();
            return null;
        });
    }

    @Override
    public void execute(String resourceName, Runnable runnable, Consumer<Throwable> fallback) {
        execute(resourceName, () -> {runnable.run();return null;}, throwable -> {fallback.accept(throwable);return null;});
    }

    private GovernanceContext createContext(String resourceName) {
        GovernanceContext context = new GovernanceContext();
        context.setResourceName(resourceName);
        context.setResourceType(GovernanceResourceType.OUTBOUND);
        return context;
    }
}

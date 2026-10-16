package com.xjtu.iron.governance.core.exception;


import com.xjtu.iron.governance.api.context.GovernanceContext;
import com.xjtu.iron.governance.api.exception.DownstreamCallException;
import com.xjtu.iron.governance.api.exception.DownstreamTimeoutException;
import com.xjtu.iron.governance.api.exception.GovernanceException;
import com.xjtu.iron.governance.spi.exception.GovernanceExceptionMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class GovernanceExceptionMapperChain {

    private final List<GovernanceExceptionMapper> mappers = new ArrayList<>();

    public GovernanceExceptionMapperChain(List<GovernanceExceptionMapper> mappers) {
        if (mappers != null) {
            this.mappers.addAll(mappers);
        }
    }

    public RuntimeException map(GovernanceContext context, Throwable throwable) {
        Throwable real = unwrap(throwable);

        if (real instanceof GovernanceException governanceException) {
            return governanceException;
        }

        for (GovernanceExceptionMapper mapper : mappers) {
            if (mapper.supports(real)) {
                return mapper.map(context, real);
            }
        }

        if (real instanceof TimeoutException) {
            return new DownstreamTimeoutException(context.getResourceName(), real);
        }

        return new DownstreamCallException(context.getResourceName(), real);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;

        while (current.getCause() != null
                && (current instanceof RuntimeException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }

        return current;
    }
}

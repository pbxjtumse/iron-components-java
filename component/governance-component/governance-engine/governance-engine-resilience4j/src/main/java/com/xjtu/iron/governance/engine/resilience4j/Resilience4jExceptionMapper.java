package com.xjtu.iron.governance.engine.resilience4j;

import com.xjtu.iron.governance.api.context.GovernanceContext;
import com.xjtu.iron.governance.api.exception.DownstreamBulkheadFullException;
import com.xjtu.iron.governance.api.exception.DownstreamCircuitOpenException;
import com.xjtu.iron.governance.api.exception.DownstreamRateLimitedException;
import com.xjtu.iron.governance.spi.exception.GovernanceExceptionMapper;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

public class Resilience4jExceptionMapper implements GovernanceExceptionMapper {

    @Override
    public boolean supports(Throwable throwable) {
        return throwable instanceof CallNotPermittedException
                || throwable instanceof BulkheadFullException
                || throwable instanceof RequestNotPermitted;
    }

    @Override
    public RuntimeException map(GovernanceContext context, Throwable throwable) {
        if (throwable instanceof CallNotPermittedException) {
            return new DownstreamCircuitOpenException(context.getResourceName(), throwable);
        }

        if (throwable instanceof BulkheadFullException) {
            return new DownstreamBulkheadFullException(context.getResourceName(), throwable);
        }

        if (throwable instanceof RequestNotPermitted) {
            return new DownstreamRateLimitedException(context.getResourceName(), throwable);
        }

        throw new IllegalArgumentException("Unsupported resilience4j exception: " + throwable.getClass());
    }
}
package com.xjtu.iron.governance.integration.spring.aop;

import com.xjtu.iron.governance.api.annotation.GovernedCall;
import com.xjtu.iron.governance.api.context.GovernanceContext;
import com.xjtu.iron.governance.core.executor.GovernanceExecutor;
import com.xjtu.iron.governance.model.resource.GovernanceResourceType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Method;

@Aspect
public class GovernedCallAspect {

    private final GovernanceExecutor governanceExecutor;

    private final FallbackMethodResolver fallbackMethodResolver = new FallbackMethodResolver();

    public GovernedCallAspect(GovernanceExecutor governanceExecutor) {
        this.governanceExecutor = governanceExecutor;
    }

    @Around("@annotation(governedCall)")
    public Object around(ProceedingJoinPoint joinPoint,
                         GovernedCall governedCall) {
        GovernanceContext context = new GovernanceContext();
        context.setResourceName(governedCall.name());
        context.setResourceType(GovernanceResourceType.OUTBOUND);
        context.setDownstream(governedCall.downstream());
        context.setOperation(governedCall.operation());
        context.setArgs(joinPoint.getArgs());

        if (governedCall.fallbackMethod() == null || governedCall.fallbackMethod().isBlank()) {
            return governanceExecutor.execute(context, ctx -> joinPoint.proceed());
        }

        return governanceExecutor.execute(
                context,
                ctx -> joinPoint.proceed(),
                (ctx, throwable) -> {
                    try {
                        return invokeFallback(joinPoint, governedCall.fallbackMethod(), throwable);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    private Object invokeFallback(ProceedingJoinPoint joinPoint,
                                  String fallbackMethodName,
                                  Throwable throwable) throws Exception {
        Object target = joinPoint.getTarget();
        Object[] args = joinPoint.getArgs();

        Method method = fallbackMethodResolver.resolve(
                target.getClass(),
                fallbackMethodName,
                args
        );

        method.setAccessible(true);

        Object[] invokeArgs;

        if (method.getParameterCount() == args.length + 1) {
            invokeArgs = new Object[args.length + 1];
            System.arraycopy(args, 0, invokeArgs, 0, args.length);
            invokeArgs[args.length] = throwable;
        } else {
            invokeArgs = args;
        }

        return method.invoke(target, invokeArgs);
    }
}
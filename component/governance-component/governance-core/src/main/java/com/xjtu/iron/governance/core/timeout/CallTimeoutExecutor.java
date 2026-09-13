package com.xjtu.iron.governance.core.timeout;


import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * 这里要注意：
 *
 * 这个超时只能限制调用方最多等待多久，不一定能真正中断底层 HTTP 调用。真实连接超时、读取超时仍然要在 Feign、OkHttp、RestTemplate、WebClient 里配置。
 这点很重要，别误以为治理层 timeout 可以完全代替 HTTP 客户端 timeout。
 */
public class CallTimeoutExecutor {

    private final ExecutorService executorService;

    public CallTimeoutExecutor(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public <T> T execute(Supplier<T> supplier, Duration timeout) throws Exception {
        Future<T> future = executorService.submit(supplier::get);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw ex;
        }
    }
}

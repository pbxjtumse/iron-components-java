package com.xjtu.iron.distributed.lock.demo.fencing;

import com.xjtu.iron.distributed.lock.api.client.DistributedLockClient;
import com.xjtu.iron.distributed.lock.api.fencing.FencingTokenGuard;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JDBC sequence fencing 与业务条件写入演示。
 */
@RestController
@RequestMapping("/demo/distributed-lock/fencing/jdbc")
@ConditionalOnBean(JdbcFencedDemoRepository.class)
public class JdbcFencingDemoController {

    /** 执行带 fencing token 的分布式锁操作。 */
    private final DistributedLockClient lockClient;

    /** 演示旧 token 被业务数据库拒绝的订单仓储。 */
    private final JdbcFencedDemoRepository repository;

    public JdbcFencingDemoController(DistributedLockClient lockClient, JdbcFencedDemoRepository repository) {
        this.lockClient = lockClient;
        this.repository = repository;
    }

    @GetMapping("/{bizKey}")
    public Map<String, Object> execute(@PathVariable String bizKey) {
        LockResult<String> result = executeWrite(bizKey, "payload:" + System.currentTimeMillis());
        return body(result, bizKey);
    }

    /**
     * 连续获得两个 token，先写入新 token，再故意使用旧 token，验证旧写被拒绝。
     */
    @GetMapping("/stale-rejection/{bizKey}")
    public Map<String, Object> staleRejection(@PathVariable String bizKey) {
        repository.delete(bizKey);
        LockResult<String> first = executeWrite(bizKey, "first");
        LockResult<String> second = executeWrite(bizKey, "second");
        long firstToken = first.fencingToken().orElseThrow();
        long secondToken = second.fencingToken().orElseThrow();
        boolean staleAccepted = repository.updateIfNewer(bizKey, "stale-overwrite", firstToken);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstToken", firstToken);
        body.put("secondToken", secondToken);
        body.put("staleWriteAccepted", staleAccepted);
        body.put("currentToken", repository.currentToken(bizKey));
        body.put("currentPayload", repository.currentPayload(bizKey));
        body.put("verified", secondToken > firstToken && !staleAccepted);
        return body;
    }

    private LockResult<String> executeWrite(String bizKey, String payload) {
        LockOptions options = LockOptions.builder()
                .namespace("demo")
                .leaseTime(Duration.ofSeconds(30))
                .waitTime(Duration.ofSeconds(2))
                .fencingRequired(true)
                .fencingTokenProviderName("mybatis-sequence")
                .build();

        return lockClient.execute("demo:jdbc-fencing:" + bizKey, options, handle -> {
            FencingTokenGuard.requireAccepted(handle,
                    token -> repository.updateIfNewer(bizKey, payload, token));
            return payload;
        });
    }

    private Map<String, Object> body(LockResult<String> result, String bizKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", result.status().name());
        body.put("stage", result.stage().name());
        body.put("acquired", result.acquired());
        body.put("value", result.value().orElse(null));
        body.put("ownerToken", result.ownerToken());
        body.put("fencingToken", result.fencingToken().orElse(null));
        body.put("fencingTokenProvider", result.fencingTokenProviderName().orElse(null));
        if (result.isSuccess()) {
            body.put("resourceToken", repository.currentToken(bizKey));
            body.put("resourcePayload", repository.currentPayload(bizKey));
        }
        return body;
    }
}

package com.xjtu.iron.idempotent.demo;

import com.xjtu.iron.idempotent.api.execution.IdempotencyExecutor;
import com.xjtu.iron.idempotent.api.execution.IdempotencyRequest;
import com.xjtu.iron.idempotent.api.execution.IdempotencyResult;
import com.xjtu.iron.idempotent.api.result.*;
import com.xjtu.iron.idempotent.api.spi.IdempotencyRequestHasher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/**
 * 用最小业务例子演示三条主链：
 *
 * <ul>
 *     <li>WINDOWED + SNAPSHOT：有限窗口内重复请求直接回放第一次结果；</li>
 *     <li>DURABLE + JDBC + SNAPSHOT：Business + SUCCESS 进入 Tx-B 本地事务闭环；</li>
 *     <li>DURABLE + REFERENCE：长期记录只保存稳定业务引用，不永久保存 DTO 快照。</li>
 * </ul>
 *
 * <p>建议对照 DefaultIdempotencyExecutor 的 [01]~[11] 注释阅读这些接口。</p>
 */
@RestController
@RequestMapping("/demo/idempotency")
public class IdempotencyDemoController {

    private final IdempotencyExecutor executor;
    private final IdempotencyRequestHasher hasher;
    private final IdempotencySnapshotPolicyFactory snapshotPolicyFactory;
    private final JdbcTemplate jdbcTemplate;

    public IdempotencyDemoController(IdempotencyExecutor executor, IdempotencyRequestHasher hasher,
                                     IdempotencySnapshotPolicyFactory snapshotPolicyFactory, JdbcTemplate jdbcTemplate) {
        this.executor = executor;
        this.hasher = hasher;
        this.snapshotPolicyFactory = snapshotPolicyFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * WINDOWED 示例。
     *
     * <p>第一次请求会真正执行 callback 并保存 SNAPSHOT；窗口内相同 key + hash 再次进入时，
     * StateMachine 走 REPLAY，callback 不会再次执行。</p>
     */
    @PostMapping("/windowed/{key}")
    public IdempotencyResult<String> windowed(@PathVariable String key, @RequestParam(defaultValue = "payload") String payload) {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .key(key)
                .requestHash(hasher.hash(Map.of("payload", payload)))
                .routeKey("demo-windowed")
                .policyName("demo-windowed")
                .build();

        IdempotencyResultPolicy<String> snapshot =
                snapshotPolicyFactory.snapshot(new IdempotencyTypeRef<String>() { });

        return executor.execute(request, snapshot,
                context -> "windowed executed, version=" + context.getVersion() + ", payload=" + payload);
    }

    /**
     * DURABLE + JDBC 本地事务示例。
     *
     * <p>transaction integration 可用时，下面的业务 INSERT、SNAPSHOT capture 和 markSuccess 会处于同一个 Tx-B。
     * failAfterBusinessWrite=true 用来验证：业务 SQL 已执行后抛异常，Tx-B 会 rollback，随后 Tx-C 独立记录 FAILED。</p>
     */
    @PostMapping("/durable/{key}")
    public IdempotencyResult<String> durable(@PathVariable String key,
                                              @RequestParam(defaultValue = "payload") String payload,
                                              @RequestParam(defaultValue = "merchant-10001") String routeKey,
                                              @RequestParam(defaultValue = "false") boolean failAfterBusinessWrite) {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .key(key)
                .routeKey(routeKey)
                .requestHash(hasher.hash(Map.of("payload", payload, "routeKey", routeKey)))
                .policyName("demo-durable")
                .build();

        IdempotencyResultPolicy<String> snapshot =
                snapshotPolicyFactory.snapshot(new IdempotencyTypeRef<String>() { });

        return executor.execute(request, snapshot, context -> {
            // 这条 SQL 就是“Business”。transaction-aware JDBC 下，它与 markSuccess 使用同一个业务事务。
            jdbcTemplate.update("INSERT INTO demo_business_record(idempotency_key,payload,created_at) VALUES (?,?,?)",
                    key, payload, Timestamp.from(Instant.now()));

            if (failAfterBusinessWrite) {
                throw new IllegalStateException("demo business failure after INSERT");
            }

            return "durable executed, generationVersion=" + context.getGenerationVersion()
                    + ", routeKey=" + context.getRouteKey() + ", payload=" + payload;
        });
    }

    /**
     * DURABLE + REFERENCE 示例。
     *
     * <p>幂等表只保存 key 作为稳定业务引用。重复请求命中 SUCCESS 后不会重新执行业务，而是调用 resolve(key)
     * 查询当前业务数据。长期幂等通常比永久保存旧 DTO JSON 更适合这种方式。</p>
     */
    @PostMapping("/durable-reference/{key}")
    public IdempotencyResult<String> durableReference(@PathVariable String key, @RequestParam(defaultValue = "payload") String payload) {
        IdempotencyRequest request = IdempotencyRequest.builder()
                .key(key)
                .routeKey("merchant-10001")
                .requestHash(hasher.hash(Map.of("payload", payload)))
                .policyName("demo-durable")
                .build();

        IdempotencyResultPolicy<String> reference = IdempotencyResultPolicies.reference(new IdempotencyResultReference<>() {
            @Override
            public String capture(String value) {
                // capture 尽量保持纯函数：从业务结果中提取稳定引用，不在这里发 MQ / 调 HTTP / 写其他数据库。
                return key;
            }

            @Override
            public String resolve(String referenceKey) {
                String currentPayload = jdbcTemplate.queryForObject(
                        "SELECT payload FROM demo_business_record WHERE idempotency_key=? ORDER BY id DESC LIMIT 1",
                        String.class, referenceKey);
                return "resolved by reference, key=" + referenceKey + ", payload=" + currentPayload;
            }
        });

        return executor.execute(request, reference, context -> {
            jdbcTemplate.update("INSERT INTO demo_business_record(idempotency_key,payload,created_at) VALUES (?,?,?)",
                    key, payload, Timestamp.from(Instant.now()));
            return "created, key=" + key + ", payload=" + payload;
        });
    }

    /**
     * 辅助验证接口：同一个 DURABLE key 正常情况下应该只产生一条合法业务记录。
     */
    @GetMapping("/durable/{key}/business-count")
    public Map<String, Object> durableBusinessCount(@PathVariable String key) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM demo_business_record WHERE idempotency_key=?", Integer.class, key);
        return Map.of("key", key, "businessCount", count == null ? 0 : count);
    }
}

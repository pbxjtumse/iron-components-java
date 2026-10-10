package com.xjtu.iron.distributed.lock.provider.mybatis.fencing;

import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenProvider;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenRequest;
import com.xjtu.iron.distributed.lock.spi.fencing.FencingTokenResponse;
import com.xjtu.iron.relational.mybatis.MyBatisAccess;
import com.xjtu.iron.relational.mybatis.MyBatisConstraintViolationDetector;
import com.xjtu.iron.relational.mybatis.MyBatisTableNameValidator;

import java.util.Objects;

/** 使用公共 MyBatis Access 在独立事务中生成严格递增 fencing token。 */
public final class MyBatisSequenceFencingTokenProvider implements FencingTokenProvider {

    /** 统一 Mapper 与 REQUIRES_NEW 事务执行入口。 */
    private final MyBatisAccess access;

    /** 已通过白名单校验的 token 技术表名。 */
    private final String tableName;

    /** 并发首次插入冲突时的最大重试次数。 */
    private final int maxRetries;

    public MyBatisSequenceFencingTokenProvider(MyBatisAccess access) {
        this(
                access,
                MyBatisFencingTokenConstants.DEFAULT_TABLE_NAME,
                MyBatisFencingTokenConstants.DEFAULT_MAX_RETRIES
        );
    }

    public MyBatisSequenceFencingTokenProvider(
            MyBatisAccess access,
            String tableName,
            int maxRetries
    ) {
        this.access = Objects.requireNonNull(access, "access must not be null");
        this.tableName = MyBatisTableNameValidator.requireValid(
                tableName,
                MyBatisFencingTokenConstants.DEFAULT_TABLE_NAME
        );
        if (maxRetries <= 0) {
            throw new IllegalArgumentException("maxRetries must be positive");
        }
        this.maxRetries = maxRetries;
    }

    @Override
    public String providerName() {
        return MyBatisFencingTokenConstants.PROVIDER_NAME;
    }

    @Override
    public boolean supports(FencingTokenRequest request) {
        return request != null
                && request.getNamespace().length() <= MyBatisFencingTokenConstants.MAX_NAMESPACE_LENGTH
                && request.getLockName().length() <= MyBatisFencingTokenConstants.MAX_LOCK_NAME_LENGTH;
    }

    @Override
    public FencingTokenResponse nextToken(FencingTokenRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Throwable lastDuplicate = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                long token = access.executeInNewTransaction(
                        "distributed-lock.next-fencing-token",
                        FencingTokenMapper.class,
                        mapper -> nextToken(mapper, request)
                );
                return FencingTokenResponse.issued(token);
            } catch (Exception failure) {
                if (MyBatisConstraintViolationDetector.isConstraintViolation(failure)
                        && attempt < maxRetries) {
                    lastDuplicate = failure;
                    continue;
                }
                return FencingTokenResponse.failed(
                        failure,
                        "failed to issue MyBatis fencing token, provider=" + providerName()
                                + ", table=" + tableName + ", attempt=" + attempt
                );
            }
        }
        return FencingTokenResponse.failed(
                lastDuplicate,
                "failed to issue MyBatis fencing token after retries: " + maxRetries
        );
    }

    private long nextToken(FencingTokenMapper mapper, FencingTokenRequest request) {
        int updated = mapper.increment(
                tableName,
                request.getNamespace(),
                request.getLockName()
        );
        if (updated == 0) {
            mapper.insertInitial(tableName, request.getNamespace(), request.getLockName());
        }
        Long token = mapper.selectCurrent(
                tableName,
                request.getNamespace(),
                request.getLockName()
        );
        if (token == null || token <= 0L) {
            throw new IllegalStateException("generated fencing token must be positive: " + token);
        }
        return token;
    }
}

package com.xjtu.iron.relational.mybatis;

import java.util.Objects;

/** 一次低基数 MyBatis 技术表访问的观测上下文。 */
public final class MyBatisAccessInvocation {

    /** 稳定操作名，不应包含任务 ID、幂等键等高基数值。 */
    private final String operationName;

    /** 本次执行使用的 Mapper 接口类型。 */
    private final Class<?> mapperType;

    public MyBatisAccessInvocation(String operationName, Class<?> mapperType) {
        if (operationName == null || operationName.isBlank()) {
            throw new IllegalArgumentException("operationName must not be blank");
        }
        this.operationName = operationName.trim();
        this.mapperType = Objects.requireNonNull(mapperType, "mapperType must not be null");
    }

    public String getOperationName() {
        return operationName;
    }

    public Class<?> getMapperType() {
        return mapperType;
    }
}

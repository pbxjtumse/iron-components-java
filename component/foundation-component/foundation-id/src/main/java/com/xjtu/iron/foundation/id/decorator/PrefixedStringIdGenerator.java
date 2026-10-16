package com.xjtu.iron.foundation.id.decorator;

import com.xjtu.iron.foundation.id.api.IdGenerationException;
import com.xjtu.iron.foundation.id.api.StringIdGenerator;

import java.util.Objects;

/** 为另一个字符串标识生成器添加固定、无业务敏感信息的前缀。 */
public final class PrefixedStringIdGenerator implements StringIdGenerator {

    /** 每个标识固定使用的前缀。 */
    private final String prefix;

    /** 前缀与主体标识之间的分隔符。 */
    private final String separator;

    /** 实际生成主体标识的委托生成器。 */
    private final StringIdGenerator delegate;

    public PrefixedStringIdGenerator(String prefix, StringIdGenerator delegate) {
        this(prefix, "", delegate);
    }

    public PrefixedStringIdGenerator(
            String prefix,
            String separator,
            StringIdGenerator delegate) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        this.prefix = prefix;
        this.separator = Objects.requireNonNull(separator, "separator must not be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public String nextId() {
        String generatedId = delegate.nextId();
        if (generatedId == null || generatedId.isBlank()) {
            throw new IdGenerationException("delegate generated a blank string id");
        }
        return prefix + separator + generatedId;
    }
}

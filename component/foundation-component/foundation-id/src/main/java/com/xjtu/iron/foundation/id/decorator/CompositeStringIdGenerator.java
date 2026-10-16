package com.xjtu.iron.foundation.id.decorator;

import com.xjtu.iron.foundation.id.api.IdGenerationException;
import com.xjtu.iron.foundation.id.api.StringIdGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/** 按固定顺序组合多个技术 ID 片段。 */
public final class CompositeStringIdGenerator implements StringIdGenerator {

    /** 按顺序生成标识片段的生成器集合。 */
    private final List<StringIdGenerator> delegates;

    /** 不同标识片段之间使用的分隔符。 */
    private final String delimiter;

    public CompositeStringIdGenerator(
            List<? extends StringIdGenerator> delegates,
            String delimiter) {
        if (delegates == null || delegates.isEmpty()) {
            throw new IllegalArgumentException("delegates must not be empty");
        }
        List<StringIdGenerator> copy = new ArrayList<>(delegates.size());
        for (StringIdGenerator delegate : delegates) {
            copy.add(Objects.requireNonNull(delegate, "delegate must not be null"));
        }
        this.delegates = List.copyOf(copy);
        this.delimiter = Objects.requireNonNull(delimiter, "delimiter must not be null");
    }

    @Override
    public String nextId() {
        StringJoiner joiner = new StringJoiner(delimiter);
        for (StringIdGenerator delegate : delegates) {
            String segment = delegate.nextId();
            if (segment == null || segment.isBlank()) {
                throw new IdGenerationException("delegate generated a blank id segment");
            }
            joiner.add(segment);
        }
        return joiner.toString();
    }
}

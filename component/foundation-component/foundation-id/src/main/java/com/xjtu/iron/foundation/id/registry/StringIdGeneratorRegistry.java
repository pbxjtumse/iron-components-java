package com.xjtu.iron.foundation.id.registry;

import com.xjtu.iron.foundation.id.api.StringIdGenerator;

import java.util.*;

/** 按用途名称管理不可变的字符串 ID 生成器集合。 */
public final class StringIdGeneratorRegistry {

    /** 按名称保存的不可变生成器映射。 */
    private final Map<String, StringIdGenerator> generators;

    public StringIdGeneratorRegistry(Map<String, ? extends StringIdGenerator> generators) {
        if (generators == null || generators.isEmpty()) {
            throw new IllegalArgumentException("generators must not be empty");
        }
        LinkedHashMap<String, StringIdGenerator> copy = new LinkedHashMap<>();
        generators.forEach((name, generator) -> copy.put(
                validateName(name),
                Objects.requireNonNull(generator, "generator must not be null")
        ));
        this.generators = Collections.unmodifiableMap(copy);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取指定生成器；名称不存在时立即失败，不静默回退到其他算法。
     *
     * @param name 生成器用途名称
     * @return 已注册生成器
     */
    public StringIdGenerator require(String name) {
        String actualName = validateName(name);
        StringIdGenerator generator = generators.get(actualName);
        if (generator == null) {
            throw new IllegalArgumentException(
                    "unknown string id generator '" + actualName
                            + "', available names: " + generators.keySet()
            );
        }
        return generator;
    }

    /**
     * 查找指定生成器。
     *
     * @param name 生成器用途名称
     * @return 查找结果
     */
    public Optional<StringIdGenerator> find(String name) {
        return Optional.ofNullable(generators.get(validateName(name)));
    }

    public boolean contains(String name) {
        return generators.containsKey(validateName(name));
    }

    public Set<String> names() {
        return generators.keySet();
    }

    public Map<String, StringIdGenerator> asMap() {
        return generators;
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("generator name must not be blank");
        }
        if (!name.equals(name.trim())) {
            throw new IllegalArgumentException(
                    "generator name must not contain leading or trailing whitespace"
            );
        }
        return name;
    }

    /** 构建时拒绝同名覆盖，避免错误配置被静默替换。 */
    public static final class Builder {

        private final Map<String, StringIdGenerator> generators = new LinkedHashMap<>();

        public Builder register(String name, StringIdGenerator generator) {
            String actualName = validateName(name);
            Objects.requireNonNull(generator, "generator must not be null");
            if (generators.putIfAbsent(actualName, generator) != null) {
                throw new IllegalArgumentException(
                        "duplicate string id generator name: " + actualName
                );
            }
            return this;
        }

        public StringIdGeneratorRegistry build() {
            return new StringIdGeneratorRegistry(generators);
        }
    }
}

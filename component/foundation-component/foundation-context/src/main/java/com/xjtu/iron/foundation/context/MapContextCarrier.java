package com.xjtu.iron.foundation.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于 Map 的上下文载体实现。
 */
public final class MapContextCarrier implements ContextCarrier {

    private final Map<String, String> values = new LinkedHashMap<>();

    public MapContextCarrier() {}

    public MapContextCarrier(Map<String, String> source) {
        if (source != null) {
            values.putAll(source);
        }
    }

    @Override
    public String get(String name) { return values.get(name); }

    @Override
    public void put(String name, String value) {
        if (name != null && value != null) {
            values.put(name, value);
        }
    }

    @Override
    public Map<String, String> asMap() { return Collections.unmodifiableMap(values); }
}

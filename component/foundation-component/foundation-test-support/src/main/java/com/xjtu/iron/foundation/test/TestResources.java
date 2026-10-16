package com.xjtu.iron.foundation.test;

import com.xjtu.iron.foundation.resource.InMemoryResource;
import com.xjtu.iron.foundation.resource.Resource;

import java.nio.charset.StandardCharsets;

/**
 * 测试资源工厂。
 */
public final class TestResources {

    private TestResources() {}

    public static Resource utf8(String location, String content) {
        return new InMemoryResource(location, content.getBytes(StandardCharsets.UTF_8));
    }
}

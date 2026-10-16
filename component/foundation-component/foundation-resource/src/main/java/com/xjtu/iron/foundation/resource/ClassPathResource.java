package com.xjtu.iron.foundation.resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * classpath 资源实现。
 */
public final class ClassPathResource implements Resource {

    private final String path;
    private final ClassLoader classLoader;

    public ClassPathResource(String path) {
        this(path, Thread.currentThread().getContextClassLoader());
    }

    public ClassPathResource(String path, ClassLoader classLoader) {
        this.path = path.startsWith("classpath:") ? path.substring("classpath:".length()) : path;
        this.classLoader = classLoader == null ? ClassPathResource.class.getClassLoader() : classLoader;
    }

    @Override public String getLocation() { return "classpath:" + path; }

    @Override public boolean exists() { return classLoader.getResource(path) != null; }

    @Override public InputStream openStream() throws IOException {
        InputStream input = classLoader.getResourceAsStream(path);
        if (input == null) { throw new ResourceNotFoundException(getLocation()); }
        return input;
    }
}

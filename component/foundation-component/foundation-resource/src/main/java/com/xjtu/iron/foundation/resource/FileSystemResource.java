package com.xjtu.iron.foundation.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件系统资源实现。
 */
public final class FileSystemResource implements Resource {

    private final Path path;

    public FileSystemResource(Path path) { this.path = path; }

    @Override public String getLocation() { return "file:" + path; }

    @Override public boolean exists() { return Files.exists(path); }

    @Override public InputStream openStream() throws IOException {
        if (!exists()) { throw new ResourceNotFoundException(getLocation()); }
        return Files.newInputStream(path);
    }
}

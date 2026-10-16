package com.xjtu.iron.foundation.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 资源读取工具，所有读取方法都要求显式给出最大字节数，避免误读大文件。
 */
public final class IronResources {

    private IronResources() {}

    public static byte[] readBytes(Resource resource, int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        try (InputStream input = resource.openStream();
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("resource is too large: " + resource.getLocation());
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("read resource failed: " + resource.getLocation(), ex);
        }
    }

    public static String readUtf8(Resource resource, int maxBytes) {
        return readString(resource, maxBytes, StandardCharsets.UTF_8);
    }

    public static String readString(Resource resource, int maxBytes, Charset charset) {
        return new String(readBytes(resource, maxBytes), charset);
    }
}

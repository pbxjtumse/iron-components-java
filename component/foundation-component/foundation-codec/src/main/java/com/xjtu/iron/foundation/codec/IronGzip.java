package com.xjtu.iron.foundation.codec;


import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GZIP 压缩工具。
 */
public final class IronGzip {

    private IronGzip() {}

    public static byte[] compress(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new byte[0];
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(bytes);
            gzip.finish();
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("gzip compress failed", ex);
        }
    }

    public static byte[] decompress(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new byte[0];
        }
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return IOUtils.toByteArray(input);
        } catch (IOException ex) {
            throw new IllegalStateException("gzip decompress failed", ex);
        }
    }
}

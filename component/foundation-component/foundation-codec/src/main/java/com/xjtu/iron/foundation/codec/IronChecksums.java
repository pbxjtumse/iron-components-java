package com.xjtu.iron.foundation.codec;

import java.util.zip.CRC32;

/**
 * 轻量校验和工具。
 */
public final class IronChecksums {

    private IronChecksums() {}

    public static long crc32(byte[] bytes) {
        CRC32 crc32 = new CRC32();
        if (bytes != null) {
            crc32.update(bytes);
        }
        return crc32.getValue();
    }
}

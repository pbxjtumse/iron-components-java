package com.xjtu.iron.foundation.serialization;

/**
 * 序列化执行阶段。
 */
public enum SerializationOperation {

    /** 将 Java 对象编码为字节或文本载荷。 */
    SERIALIZE,

    /** 将字节或文本载荷还原为 Java 对象。 */
    DESERIALIZE
}

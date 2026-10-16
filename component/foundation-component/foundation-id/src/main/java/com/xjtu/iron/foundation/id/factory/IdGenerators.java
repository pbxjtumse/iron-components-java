package com.xjtu.iron.foundation.id.factory;

import com.xjtu.iron.foundation.id.api.LongIdGenerator;
import com.xjtu.iron.foundation.id.api.StringIdGenerator;
import com.xjtu.iron.foundation.id.decorator.CompositeStringIdGenerator;
import com.xjtu.iron.foundation.id.decorator.PrefixedStringIdGenerator;
import com.xjtu.iron.foundation.id.nanoid.NanoIdOptions;
import com.xjtu.iron.foundation.id.nanoid.NanoIdStringIdGenerator;
import com.xjtu.iron.foundation.id.snowflake.SnowflakeLongIdGenerator;
import com.xjtu.iron.foundation.id.snowflake.SnowflakeOptions;
import com.xjtu.iron.foundation.id.ulid.UlidStringIdGenerator;
import com.xjtu.iron.foundation.id.uuid.UuidV4StringIdGenerator;
import com.xjtu.iron.foundation.id.uuid.UuidV7StringIdGenerator;

import java.util.List;

/** 创建一期内置 ID 生成器和装饰器的统一入口。 */
public final class IdGenerators {

    private IdGenerators() {
    }

    public static StringIdGenerator uuidV4() {
        return new UuidV4StringIdGenerator();
    }

    public static StringIdGenerator uuidV7() {
        return new UuidV7StringIdGenerator();
    }

    public static StringIdGenerator ulid() {
        return new UlidStringIdGenerator();
    }

    public static StringIdGenerator nanoId() {
        return new NanoIdStringIdGenerator();
    }

    public static StringIdGenerator nanoId(NanoIdOptions options) {
        return new NanoIdStringIdGenerator(options);
    }

    public static LongIdGenerator snowflake(long workerId) {
        return new SnowflakeLongIdGenerator(
                SnowflakeOptions.builder(workerId).build()
        );
    }

    public static LongIdGenerator snowflake(SnowflakeOptions options) {
        return new SnowflakeLongIdGenerator(options);
    }

    public static StringIdGenerator prefixed(
            String prefix,
            StringIdGenerator delegate) {
        return new PrefixedStringIdGenerator(prefix, delegate);
    }

    public static StringIdGenerator prefixed(
            String prefix,
            String separator,
            StringIdGenerator delegate) {
        return new PrefixedStringIdGenerator(prefix, separator, delegate);
    }

    public static StringIdGenerator composite(
            List<? extends StringIdGenerator> delegates,
            String delimiter) {
        return new CompositeStringIdGenerator(delegates, delimiter);
    }
}

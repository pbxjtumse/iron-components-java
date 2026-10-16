package com.xjtu.iron.foundation.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StandardContextCodecTest {

    @Test
    void writeAndReadShouldKeepStandardKeys() {
        ExecutionContext context = ExecutionContext.builder()
                .put(StandardContextKeys.REQUEST_ID, "r1")
                .put(StandardContextKeys.TENANT_ID, "t1")
                .build();
        MapContextCarrier carrier = new MapContextCarrier();
        StandardContextCodec codec = new StandardContextCodec();
        codec.write(context, carrier);
        assertThat(codec.read(carrier).get(StandardContextKeys.REQUEST_ID)).contains("r1");
    }
}

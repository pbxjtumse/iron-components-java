package com.xjtu.iron.storage.routing.core;

import com.xjtu.iron.storage.routing.api.CompositeShardKey;
import com.xjtu.iron.storage.routing.api.ShardKey;
import com.xjtu.iron.storage.routing.api.ShardValue;
import com.xjtu.iron.storage.routing.api.StorageRoutingException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShardKeyModelTest {

    @Test
    void valuesShouldRetainTypeAndExactStringOrDecimalRepresentation() {
        assertThat(ShardValue.of("8").type()).isEqualTo(ShardValue.Type.STRING);
        assertThat(ShardValue.of(8).type()).isEqualTo(ShardValue.Type.INTEGER);
        assertThat(ShardValue.of(8L).type()).isEqualTo(ShardValue.Type.LONG);
        assertThat(ShardValue.of("8")).isNotEqualTo(ShardValue.of(8));
        assertThat(ShardValue.of(8L).value()).isEqualTo(8L);
        assertThat(ShardValue.of(" 8 ").canonicalText()).isEqualTo(" 8 ");
        assertThat(ShardValue.of("").canonicalText()).isEmpty();
        assertThat(ShardValue.of(new BigDecimal("1.00")).canonicalText()).isEqualTo("1.00");
        assertThat(ShardValue.of(new BigDecimal("1.00"))).isNotEqualTo(ShardValue.of(new BigDecimal("1.0")));
    }

    @Test
    void arbitraryOrMutableValuesShouldNotReachHashingThroughToString() {
        List<Object> unsupported = List.of(new Object(), new StringBuilder("8"), new AtomicInteger(8),
                new byte[]{8}, List.of("8"), 8.0, new BigInteger("8") { });
        for (Object value : unsupported) {
            assertThatThrownBy(() -> ShardValue.of(value)).isInstanceOf(StorageRoutingException.class)
                    .hasMessageContaining("Unsupported shard value type");
        }
        assertThatThrownBy(() -> ShardValue.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void singleAndCompositeKeysShouldUseTheSameImmutableCollectionModel() {
        ShardKey tenant = ShardKey.of("tenant_id", 42L);
        ShardKey order = ShardKey.of("order_id", "8");
        List<ShardKey> original = new ArrayList<>(List.of(tenant, order));
        CompositeShardKey composite = CompositeShardKey.from(original);
        original.clear();

        assertThat(composite.keys()).containsExactly(tenant, order);
        assertThatThrownBy(() -> composite.keys().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(CompositeShardKey.of(tenant).singleKey()).isSameAs(tenant);
        assertThatThrownBy(composite::singleKey).isInstanceOf(StorageRoutingException.class);
        assertThat(composite).isEqualTo(CompositeShardKey.of(tenant, order));
        assertThat(composite.hashCode()).isEqualTo(CompositeShardKey.of(tenant, order).hashCode());
    }

    @Test
    void shouldRejectEmptyKeysDuplicateNamesAndMissingFields() {
        assertThatThrownBy(() -> CompositeShardKey.of()).isInstanceOf(StorageRoutingException.class);
        assertThatThrownBy(() -> CompositeShardKey.of(ShardKey.of("id", 8), ShardKey.of(" id ", 9)))
                .isInstanceOf(StorageRoutingException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> CompositeShardKey.of(ShardKey.of("id", 8), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ShardKey.of(" ", 8)).isInstanceOf(StorageRoutingException.class);
    }

    @Test
    void canonicalV1EncodingShouldHaveAnExplicitStableWireForm() {
        CompositeShardKey key = CompositeShardKey.of(ShardKey.of("tenant_id", 42L), ShardKey.of("order_id", "8"));

        assertThat(key.canonicalForm()).isEqualTo("v1;2;9:tenant_id4:LONG2:428:order_id6:STRING1:8");
    }

    @Test
    void encodingShouldPreserveBoundariesTypesNamesAndOrder() {
        CompositeShardKey left = CompositeShardKey.of(ShardKey.of("x", "ab"), ShardKey.of("y", "c"));
        CompositeShardKey right = CompositeShardKey.of(ShardKey.of("x", "a"), ShardKey.of("y", "bc"));
        CompositeShardKey typed = CompositeShardKey.of(ShardKey.of("x", 8L), ShardKey.of("y", "c"));

        assertThat(left.canonicalForm()).isNotEqualTo(right.canonicalForm());
        assertThat(typed.canonicalForm()).isNotEqualTo(CompositeShardKey.of(ShardKey.of("x", "8"), ShardKey.of("y", "c")).canonicalForm());
        assertThat(left.canonicalForm()).isNotEqualTo(CompositeShardKey.of(left.keys().get(1), left.keys().get(0)).canonicalForm());
        assertThat(left.canonicalForm()).isNotEqualTo(CompositeShardKey.of(ShardKey.of("z", "ab"), ShardKey.of("y", "c")).canonicalForm());
    }

    @Test
    void encodingShouldHandleDelimitersEmptyStringsAndSupplementaryUnicode() {
        CompositeShardKey key = CompositeShardKey.of(ShardKey.of("x", ":;"), ShardKey.of("y", ""), ShardKey.of("z", "😀"));

        assertThat(key.canonicalForm()).isEqualTo("v1;3;1:x6:STRING2::;1:y6:STRING0:1:z6:STRING2:😀");
    }
}

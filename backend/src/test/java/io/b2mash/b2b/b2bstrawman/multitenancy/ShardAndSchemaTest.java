package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ShardAndSchema} validation, focused on D7: single-character shard IDs are
 * now accepted while a trailing underscore / leading digit / over-length are still rejected. See
 * kazi-infra-review-scheduling-sharding.md finding D7.
 */
class ShardAndSchemaTest {

  private static final String SCHEMA = "tenant_a1b2c3d4e5f6";

  @Test
  void acceptsSingleCharacterShardId() {
    assertThatCode(() -> new ShardAndSchema("a", "public")).doesNotThrowAnyException();
    assertThat(ShardAndSchema.format("a", SCHEMA)).isEqualTo("a:" + SCHEMA);
    assertThat(ShardAndSchema.parse("a:" + SCHEMA).shardId()).isEqualTo("a");
  }

  @Test
  void acceptsPrimaryAndTypicalIds() {
    assertThatCode(() -> new ShardAndSchema("primary", "public")).doesNotThrowAnyException();
    assertThatCode(() -> new ShardAndSchema("kazi_legal_1", SCHEMA)).doesNotThrowAnyException();
    assertThatCode(() -> new ShardAndSchema("s2", SCHEMA)).doesNotThrowAnyException();
  }

  @Test
  void acceptsMaxLengthShardId() {
    String maxLen = "a".repeat(50);
    assertThatCode(() -> new ShardAndSchema(maxLen, SCHEMA)).doesNotThrowAnyException();
  }

  @Test
  void rejectsTrailingUnderscore() {
    assertThatThrownBy(() -> new ShardAndSchema("a_", "public"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ShardAndSchema("shard_", "public"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsLeadingDigitUppercaseAndBlank() {
    assertThatThrownBy(() -> new ShardAndSchema("1ab", "public"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ShardAndSchema("Ab", "public"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ShardAndSchema("", "public"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsOverLengthShardId() {
    String tooLong = "a".repeat(51);
    assertThatThrownBy(() -> new ShardAndSchema(tooLong, "public"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ShardAndSchema}.
 *
 * <p>Covers the {@code parse}/{@code format} composite-identifier contract (round-trip, {@code
 * DEFAULT} constant, rejection messages) and the D7 constructor-validation rules: single-character
 * shard IDs are now accepted while a trailing underscore / leading digit / over-length are still
 * rejected. See kazi-infra-review-scheduling-sharding.md finding D7.
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

  @Test
  void parsesValidCompositeIdentifier() {
    var result = ShardAndSchema.parse("primary:public");
    assertThat(result.shardId()).isEqualTo("primary");
    assertThat(result.schemaName()).isEqualTo("public");
  }

  @Test
  void parsesSecondaryShardWithTenantSchema() {
    var result = ShardAndSchema.parse("kazi_legal_1:tenant_aabbccddeeff");
    assertThat(result.shardId()).isEqualTo("kazi_legal_1");
    assertThat(result.schemaName()).isEqualTo("tenant_aabbccddeeff");
  }

  @Test
  void formatProducesCorrectString() {
    assertThat(ShardAndSchema.format("primary", "public")).isEqualTo("primary:public");
    assertThat(ShardAndSchema.format("kazi_legal_1", "tenant_aabbccddeeff"))
        .isEqualTo("kazi_legal_1:tenant_aabbccddeeff");
  }

  @Test
  void roundTripParseAndFormat() {
    var original = new ShardAndSchema("demo", "tenant_112233445566");
    var formatted = ShardAndSchema.format(original.shardId(), original.schemaName());
    var parsed = ShardAndSchema.parse(formatted);
    assertThat(parsed).isEqualTo(original);
  }

  @Test
  void defaultConstantIsPrimaryPublic() {
    assertThat(ShardAndSchema.DEFAULT.shardId()).isEqualTo("primary");
    assertThat(ShardAndSchema.DEFAULT.schemaName()).isEqualTo("public");
  }

  @Test
  void rejectsMissingColon() {
    assertThatThrownBy(() -> ShardAndSchema.parse("primarypublic"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing colon");
  }

  @Test
  void rejectsInvalidShardIdChars() {
    assertThatThrownBy(() -> ShardAndSchema.parse("UPPER:public"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid shard ID");
  }

  @Test
  void rejectsInvalidSchemaName() {
    assertThatThrownBy(() -> ShardAndSchema.parse("primary:bad_schema"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid schema name");
  }

  @Test
  void rejectsNullInput() {
    assertThatThrownBy(() -> ShardAndSchema.parse(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankInput() {
    assertThatThrownBy(() -> ShardAndSchema.parse("")).isInstanceOf(IllegalArgumentException.class);
  }
}

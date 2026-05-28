package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class ShardConfigMigrationTest {

  @Autowired private ShardConfigRepository shardConfigRepository;

  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void v25MigrationCreatesShardConfigTable() {
    var count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = 'shard_config'",
            Integer.class);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void v25MigrationSeedsPrimaryShardRow() {
    var primary = shardConfigRepository.findById("primary");
    assertThat(primary).isPresent();
    assertThat(primary.get().getDisplayName()).isEqualTo("Primary Database");
    assertThat(primary.get().isActive()).isTrue();
    assertThat(primary.get().getPoolSize()).isEqualTo(25);
    assertThat(primary.get().isReadOnly()).isFalse();
  }

  @Test
  void orgSchemaMappingShardIdDefaultsToPrimary() {
    var mapping = new OrgSchemaMapping("test-org-shard-default", "tenant_aabbccddeeff");
    orgSchemaMappingRepository.saveAndFlush(mapping);

    var found = orgSchemaMappingRepository.findByExternalOrgId("test-org-shard-default");
    assertThat(found).isPresent();
    assertThat(found.get().getShardId()).isEqualTo("primary");
  }

  @Test
  void shardConfigEntityRoundTrip() {
    var config = new ShardConfig("test_shard_1", "Test Shard One");
    config.setJdbcUrl("jdbc:postgresql://shard1.example.com:5432/app");
    config.setUsername("shard_user");
    config.setPoolSize(10);

    shardConfigRepository.saveAndFlush(config);

    var found = shardConfigRepository.findById("test_shard_1");
    assertThat(found).isPresent();
    assertThat(found.get().getDisplayName()).isEqualTo("Test Shard One");
    assertThat(found.get().getJdbcUrl()).isEqualTo("jdbc:postgresql://shard1.example.com:5432/app");
    assertThat(found.get().getUsername()).isEqualTo("shard_user");
    assertThat(found.get().getPoolSize()).isEqualTo(10);
    assertThat(found.get().isActive()).isTrue();
    assertThat(found.get().isReadOnly()).isFalse();
    assertThat(found.get().getCreatedAt()).isNotNull();
    assertThat(found.get().getUpdatedAt()).isNotNull();
  }

  @Nested
  class ShardAndSchemaParsingTest {

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
      assertThatThrownBy(() -> ShardAndSchema.parse(""))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}

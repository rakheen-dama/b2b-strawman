package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
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

  private final ShardConfigRepository shardConfigRepository;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final JdbcTemplate jdbcTemplate;

  @Autowired
  ShardConfigMigrationTest(
      ShardConfigRepository shardConfigRepository,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      JdbcTemplate jdbcTemplate) {
    this.shardConfigRepository = shardConfigRepository;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

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
    assertThat(primary.get().getPoolSize()).isEqualTo(10);
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
}

package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
// @TestPropertySource is required here: application-test.yml disables sharding globally, but this
// test class needs the DefaultShardRegistry bean active. This genuinely varies per test class per
// the anti-pattern policy in backend/CLAUDE.md (cannot move to application-test.yml).
@TestPropertySource(properties = "kazi.sharding.enabled=true")
@Transactional
class ShardRegistryTest {

  private final ShardRegistry shardRegistry;
  private final DataSource primaryDataSource;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;

  @Autowired
  ShardRegistryTest(
      ShardRegistry shardRegistry,
      DataSource primaryDataSource,
      OrgSchemaMappingRepository orgSchemaMappingRepository) {
    this.shardRegistry = shardRegistry;
    this.primaryDataSource = primaryDataSource;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
  }

  @Test
  void startupWithPrimaryOnlyReturnsOneActiveShard() {
    assertThat(shardRegistry.getActiveShardIds()).containsExactly("primary");
  }

  @Test
  void getPrimaryDataSourceReturnsSpringManagedDataSource() {
    assertThat(shardRegistry.getPrimaryDataSource()).isSameAs(primaryDataSource);
  }

  @Test
  void getDataSourceForPrimaryReturnsPrimaryDataSource() {
    assertThat(shardRegistry.getDataSource("primary")).isSameAs(primaryDataSource);
  }

  @Test
  void getDataSourceForUnknownShardThrowsException() {
    assertThatThrownBy(() -> shardRegistry.getDataSource("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown or inactive shard");
  }

  @Test
  void findByShardIdReturnsMappingsForPrimary() {
    // Create a test mapping on the primary shard
    var mapping = new OrgSchemaMapping("test-org-registry", "tenant_112233445566");
    orgSchemaMappingRepository.saveAndFlush(mapping);

    var mappings = orgSchemaMappingRepository.findByShardId("primary");
    assertThat(mappings)
        .extracting(OrgSchemaMapping::getExternalOrgId)
        .contains("test-org-registry");
  }
}

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
@TestPropertySource(properties = "kazi.sharding.enabled=true")
@Transactional
class ShardRegistryTest {

  @Autowired private ShardRegistry shardRegistry;

  @Autowired private DataSource primaryDataSource;

  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

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

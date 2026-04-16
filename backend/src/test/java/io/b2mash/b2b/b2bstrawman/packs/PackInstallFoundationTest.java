package io.b2mash.b2b.b2bstrawman.packs;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PackInstallFoundationTest {

  private static final String ORG_ID = "org_pack_install_foundation_test";

  @Autowired private PackInstallRepository packInstallRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ObjectMapper objectMapper;

  private String schemaName;

  @BeforeAll
  void provisionTenant() {
    schemaName =
        provisioningService
            .provisionTenant(ORG_ID, "Pack Install Foundation Test Org", null)
            .schemaName();
  }

  @Test
  void v94MigrationAppliesWithoutError() {
    // If we got here, provisioning ran all migrations including V94 successfully.
    // Verify the table exists by attempting a query.
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var all = packInstallRepository.findAll();
                      assertThat(all).isNotNull();
                    }));
  }

  @Test
  void packInstallCrudAndFindByPackId() {
    ScopedValue.where(RequestScopes.TENANT_ID, schemaName)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var memberId = UUID.randomUUID();
                      var now = Instant.now();

                      var packInstall =
                          new PackInstall(
                              "test-doc-pack",
                              PackType.DOCUMENT_TEMPLATE,
                              "1.0.0",
                              "Test Document Pack",
                              now,
                              memberId,
                              5);

                      var saved = packInstallRepository.save(packInstall);
                      packInstallRepository.flush();

                      assertThat(saved.getId()).isNotNull();

                      var found = packInstallRepository.findByPackId("test-doc-pack");
                      assertThat(found).isPresent();

                      var loaded = found.get();
                      assertThat(loaded.getPackId()).isEqualTo("test-doc-pack");
                      assertThat(loaded.getPackType()).isEqualTo(PackType.DOCUMENT_TEMPLATE);
                      assertThat(loaded.getPackVersion()).isEqualTo("1.0.0");
                      assertThat(loaded.getPackName()).isEqualTo("Test Document Pack");
                      assertThat(loaded.getInstalledAt()).isNotNull();
                      assertThat(loaded.getInstalledByMemberId()).isEqualTo(memberId);
                      assertThat(loaded.getItemCount()).isEqualTo(5);

                      // findByPackId for non-existent pack returns empty
                      assertThat(packInstallRepository.findByPackId("nonexistent")).isEmpty();
                    }));
  }

  @Test
  void computeHashProducesConsistentSha256() {
    String input = "{\"key\":\"value\",\"number\":42}";
    String hash1 = ContentHashUtil.computeHash(input);
    String hash2 = ContentHashUtil.computeHash(input);

    assertThat(hash1).isEqualTo(hash2);
    assertThat(hash1).hasSize(64); // SHA-256 hex = 64 chars
    assertThat(hash1).matches("[0-9a-f]+");

    // Different input produces different hash
    String differentHash = ContentHashUtil.computeHash("{\"different\":\"content\"}");
    assertThat(differentHash).isNotEqualTo(hash1);
  }

  @Test
  void canonicalizeJsonSortsKeysAndStripsWhitespace() {
    // Create JSON with keys in non-alphabetical order
    ObjectNode node = (ObjectNode) objectMapper.createObjectNode();
    node.put("zebra", "last");
    node.put("apple", "first");
    node.put("middle", 42);

    String canonical = ContentHashUtil.canonicalizeJson(node);

    // Keys should be sorted alphabetically
    assertThat(canonical).isEqualTo("{\"apple\":\"first\",\"middle\":42,\"zebra\":\"last\"}");

    // Same content in different order produces same canonical form
    ObjectNode node2 = (ObjectNode) objectMapper.createObjectNode();
    node2.put("middle", 42);
    node2.put("apple", "first");
    node2.put("zebra", "last");

    String canonical2 = ContentHashUtil.canonicalizeJson(node2);
    assertThat(canonical2).isEqualTo(canonical);
  }
}

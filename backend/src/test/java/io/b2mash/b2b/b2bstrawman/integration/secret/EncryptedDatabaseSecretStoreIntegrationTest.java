package io.b2mash.b2b.b2bstrawman.integration.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EncryptedDatabaseSecretStoreIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID_A = "org_secret_test_a";
  private static final String ORG_ID_B = "org_secret_test_b";

  @Autowired private MockMvc mockMvc;
  @Autowired private SecretStore secretStore;
  @Autowired private OrgSecretRepository orgSecretRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchemaA;
  private String tenantSchemaB;
  private UUID memberIdA;
  private UUID memberIdB;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID_A, "Secret Test Org A");
    planSyncService.syncPlan(ORG_ID_A, "pro-plan");
    memberIdA =
        UUID.fromString(
            syncMember(ORG_ID_A, "user_secret_a", "secret_a@test.com", "Secret A", "owner"));
    tenantSchemaA =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_A).orElseThrow().getSchemaName();

    provisioningService.provisionTenant(ORG_ID_B, "Secret Test Org B");
    planSyncService.syncPlan(ORG_ID_B, "pro-plan");
    memberIdB =
        UUID.fromString(
            syncMember(ORG_ID_B, "user_secret_b", "secret_b@test.com", "Secret B", "owner"));
    tenantSchemaB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID_B).orElseThrow().getSchemaName();
  }

  @Test
  void shouldStoreAndRetrieveSecret() {
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdA,
        "owner",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  secretStore.store("test:api_key", "my-secret-value");
                  String retrieved = secretStore.retrieve("test:api_key");
                  assertThat(retrieved).isEqualTo("my-secret-value");
                }));
  }

  @Test
  void shouldProduceUniqueIvForSamePlaintext() {
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdA,
        "owner",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  secretStore.store("iv-test-1", "same-value");
                  secretStore.store("iv-test-2", "same-value");

                  var secret1 = orgSecretRepository.findBySecretKey("iv-test-1").orElseThrow();
                  var secret2 = orgSecretRepository.findBySecretKey("iv-test-2").orElseThrow();

                  assertThat(secret1.getIv()).isNotEqualTo(secret2.getIv());
                  assertThat(secret1.getEncryptedValue()).isNotEqualTo(secret2.getEncryptedValue());
                }));
  }

  @Test
  void shouldThrowWhenRetrievingMissingSecret() {
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdA,
        "owner",
        () ->
            assertThatThrownBy(() -> secretStore.retrieve("nonexistent:key"))
                .isInstanceOf(ResourceNotFoundException.class));
  }

  @Test
  void shouldDeleteSecret() {
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdA,
        "owner",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  secretStore.store("delete-test", "to-be-deleted");
                  assertThat(secretStore.exists("delete-test")).isTrue();

                  secretStore.delete("delete-test");
                  assertThat(secretStore.exists("delete-test")).isFalse();
                }));
  }

  @Test
  void shouldCheckExistence() {
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdA,
        "owner",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  assertThat(secretStore.exists("existence-check")).isFalse();
                  secretStore.store("existence-check", "some-value");
                  assertThat(secretStore.exists("existence-check")).isTrue();
                }));
  }

  @Test
  void shouldUpsertExistingSecret() {
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdA,
        "owner",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  secretStore.store("upsert-test", "original-value");
                  assertThat(secretStore.retrieve("upsert-test")).isEqualTo("original-value");

                  secretStore.store("upsert-test", "updated-value");
                  assertThat(secretStore.retrieve("upsert-test")).isEqualTo("updated-value");
                }));
  }

  @Test
  void shouldIsolateBetweenTenants() {
    runInTenant(
        tenantSchemaA,
        ORG_ID_A,
        memberIdA,
        "owner",
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> secretStore.store("cross-tenant-test", "tenant-a-secret")));

    runInTenant(
        tenantSchemaB,
        ORG_ID_B,
        memberIdB,
        "owner",
        () -> {
          assertThat(secretStore.exists("cross-tenant-test")).isFalse();
          assertThatThrownBy(() -> secretStore.retrieve("cross-tenant-test"))
              .isInstanceOf(ResourceNotFoundException.class);
        });
  }

  @Test
  void shouldFailFastWithMissingEncryptionKey() {
    var store = new EncryptedDatabaseSecretStore(orgSecretRepository, "");
    assertThatThrownBy(store::validateKey)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("INTEGRATION_ENCRYPTION_KEY");
  }

  @Test
  void shouldFailFastWithWrongKeyLength() {
    String shortKey = Base64.getEncoder().encodeToString("sixteen-byte-key".getBytes());
    var store = new EncryptedDatabaseSecretStore(orgSecretRepository, shortKey);
    assertThatThrownBy(store::validateKey)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32-byte");
  }

  // --- Helpers ---

  private void runInTenant(
      String schema, String orgId, UUID memberId, String role, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, role)
        .run(action);
  }

  private String syncMember(
      String orgId, String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clerkOrgId": "%s",
                          "clerkUserId": "%s",
                          "email": "%s",
                          "name": "%s",
                          "avatarUrl": null,
                          "orgRole": "%s"
                        }
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }
}

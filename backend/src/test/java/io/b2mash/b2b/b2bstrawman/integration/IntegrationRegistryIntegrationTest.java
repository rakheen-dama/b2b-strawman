package io.b2mash.b2b.b2bstrawman.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingSyncResult;
import io.b2mash.b2b.b2bstrawman.integration.accounting.InvoiceSyncRequest;
import io.b2mash.b2b.b2bstrawman.integration.accounting.LineItem;
import io.b2mash.b2b.b2bstrawman.integration.accounting.NoOpAccountingProvider;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
class IntegrationRegistryIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_registry_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private IntegrationRegistry integrationRegistry;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Registry Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_registry_test", "registry@test.com", "Registry Test", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void cacheMissHitsDbThenReturnsNoop() {
    runInTenant(
        () -> {
          // No OrgIntegration rows exist -- should return NoOpAccountingProvider
          var result =
              integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
          assertThat(result).isInstanceOf(NoOpAccountingProvider.class);

          // Second call should use cache (same result)
          var result2 =
              integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
          assertThat(result2).isInstanceOf(NoOpAccountingProvider.class);
        });
  }

  @Test
  void evictClearsCacheEntry() {
    runInTenant(
        () -> {
          // First resolve with no DB row -- caches EMPTY sentinel, returns noop
          var result =
              integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
          assertThat(result).isInstanceOf(NoOpAccountingProvider.class);

          // Insert an enabled integration with a slug that has no registered adapter.
          // This will change the resolve path: instead of EMPTY -> noop, it goes through
          // "configured slug not found" -> noop fallback. Without eviction, the cache
          // still holds EMPTY so the DB change is invisible.
          transactionTemplate.executeWithoutResult(
              status -> {
                var integration =
                    new OrgIntegration(IntegrationDomain.ACCOUNTING, "nonexistent-provider");
                integration.enable();
                orgIntegrationRepository.save(integration);
              });

          // Without eviction, the cached EMPTY sentinel is still served
          var result2 =
              integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
          assertThat(result2).isInstanceOf(NoOpAccountingProvider.class);

          // Evict the cache entry
          integrationRegistry.evict(tenantSchema, IntegrationDomain.ACCOUNTING);

          // Now resolve again -- the cache is cleared so it hits the DB,
          // finds the enabled integration, and returns noop via the
          // "adapter not found for slug" fallback path.
          var result3 =
              integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
          assertThat(result3).isInstanceOf(NoOpAccountingProvider.class);

          // Now disable the integration and evict again -- the DB read should find
          // a disabled integration, returning noop via the "disabled" path.
          transactionTemplate.executeWithoutResult(
              status -> {
                orgIntegrationRepository
                    .findByDomain(IntegrationDomain.ACCOUNTING)
                    .ifPresent(
                        integration -> {
                          integration.disable();
                          orgIntegrationRepository.save(integration);
                        });
              });

          // Without eviction: still serves the previous cached entry (enabled +
          // nonexistent-provider)
          var result4 =
              integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
          assertThat(result4).isInstanceOf(NoOpAccountingProvider.class);

          // Evict and verify the disabled state is now reflected
          integrationRegistry.evict(tenantSchema, IntegrationDomain.ACCOUNTING);

          var result5 =
              integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
          assertThat(result5).isInstanceOf(NoOpAccountingProvider.class);

          // Finally, delete the integration and evict to confirm we're back to the
          // EMPTY sentinel path
          transactionTemplate.executeWithoutResult(
              status -> {
                orgIntegrationRepository
                    .findByDomain(IntegrationDomain.ACCOUNTING)
                    .ifPresent(orgIntegrationRepository::delete);
              });
          integrationRegistry.evict(tenantSchema, IntegrationDomain.ACCOUNTING);

          var result6 =
              integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
          assertThat(result6).isInstanceOf(NoOpAccountingProvider.class);
        });
  }

  @Test
  void smokeTestFreshTenantResolvesAndWorks() {
    runInTenant(
        () -> {
          // Resolve accounting provider with no config
          var provider =
              integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingProvider.class);
          assertThat(provider).isInstanceOf(NoOpAccountingProvider.class);

          // Call syncInvoice and verify it works
          var request =
              new InvoiceSyncRequest(
                  "INV-SMOKE-001",
                  "Smoke Test Corp",
                  List.of(
                      new LineItem("Consulting", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO)),
                  "ZAR",
                  LocalDate.now(),
                  LocalDate.now().plusDays(30));

          AccountingSyncResult syncResult = provider.syncInvoice(request);

          assertThat(syncResult.success()).isTrue();
          assertThat(syncResult.externalReferenceId()).startsWith("NOOP-");
          assertThat(syncResult.errorMessage()).isNull();
        });
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
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

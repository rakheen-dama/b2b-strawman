package io.b2mash.b2b.b2bstrawman.clause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.DocumentTemplateRepository;
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
class AccountingClausePackSeederTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_acps_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private TemplateClauseRepository templateClauseRepository;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Accounting Clause Pack Test Org", "accounting-za");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    syncMember(ORG_ID, "user_acps_owner", "acps_owner@test.com", "ACPS Owner", "owner");
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void createsSevenAccountingClauses() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clauses =
                      clauseRepository.findByPackIdAndSourceAndActiveTrue(
                          "accounting-za-clauses", ClauseSource.SYSTEM);
                  assertThat(clauses).hasSize(7);
                }));
  }

  @Test
  void createsTemplateClauseAssociations() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Bookkeeping engagement letter should have 7 clause associations
                  var bookkeepingTemplate =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(
                              "accounting-za", "engagement-letter-bookkeeping")
                          .orElseThrow();
                  var bookkeepingAssocs =
                      templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(
                          bookkeepingTemplate.getId());
                  assertThat(bookkeepingAssocs).hasSize(7);

                  // Tax return engagement letter should have 5 clause associations
                  var taxReturnTemplate =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(
                              "accounting-za", "engagement-letter-tax-return")
                          .orElseThrow();
                  var taxReturnAssocs =
                      templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(
                          taxReturnTemplate.getId());
                  assertThat(taxReturnAssocs).hasSize(5);

                  // Advisory engagement letter should have 4 clause associations
                  var advisoryTemplate =
                      documentTemplateRepository
                          .findByPackIdAndPackTemplateKey(
                              "accounting-za", "engagement-letter-advisory")
                          .orElseThrow();
                  var advisoryAssocs =
                      templateClauseRepository.findByTemplateIdOrderBySortOrderAsc(
                          advisoryTemplate.getId());
                  assertThat(advisoryAssocs).hasSize(4);
                }));
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
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

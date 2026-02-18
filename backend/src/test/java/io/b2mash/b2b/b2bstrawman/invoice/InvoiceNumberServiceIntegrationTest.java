package io.b2mash.b2b.b2bstrawman.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class InvoiceNumberServiceIntegrationTest {

  private static final String API_KEY = "test-api-key";

  // Each test gets its own org to avoid counter interference.
  // Each tenant has its own dedicated schema, so counters are independent per schema.
  private static final String ORG_ID_FIRST = "org_inv_num_first";
  private static final String ORG_ID_SEQ = "org_inv_num_seq";
  private static final String ORG_ID_CONC = "org_inv_num_conc";
  private static final String ORG_ID_INDEP_X = "org_inv_num_ix";
  private static final String ORG_ID_INDEP_Y = "org_inv_num_iy";

  @Autowired private MockMvc mockMvc;
  @Autowired private InvoiceNumberService invoiceNumberService;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String schemaFirst;
  private String schemaSeq;
  private String schemaConc;
  private String schemaIndepX;
  private String schemaIndepY;

  @BeforeAll
  void setup() throws Exception {
    schemaFirst = provisionAndGetSchema(ORG_ID_FIRST, "First Test Org", "user_inv_first");
    schemaSeq = provisionAndGetSchema(ORG_ID_SEQ, "Seq Test Org", "user_inv_seq");
    schemaConc = provisionAndGetSchema(ORG_ID_CONC, "Conc Test Org", "user_inv_conc");
    schemaIndepX = provisionAndGetSchema(ORG_ID_INDEP_X, "Indep X Org", "user_inv_ix");
    schemaIndepY = provisionAndGetSchema(ORG_ID_INDEP_Y, "Indep Y Org", "user_inv_iy");
  }

  @Test
  void firstCallReturnsInv0001() {
    // In shared schema, the counter discriminator is the orgId (not the schema name)
    var result =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaFirst)
            .call(() -> transactionTemplate.execute(tx -> invoiceNumberService.assignNumber()));
    assertThat(result).isEqualTo("INV-0001");
  }

  @Test
  void secondCallReturnsInv0002() {
    var result1 =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaSeq)
            .call(() -> transactionTemplate.execute(tx -> invoiceNumberService.assignNumber()));
    var result2 =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaSeq)
            .call(() -> transactionTemplate.execute(tx -> invoiceNumberService.assignNumber()));
    assertThat(result1).isEqualTo("INV-0001");
    assertThat(result2).isEqualTo("INV-0002");
  }

  @Test
  void concurrentCallsProduceDistinctNumbers() throws Exception {
    var latch = new CountDownLatch(1);
    var results = new ConcurrentLinkedQueue<String>();
    var errors = new ConcurrentLinkedQueue<Throwable>();
    var executor = Executors.newFixedThreadPool(2);

    for (int i = 0; i < 2; i++) {
      executor.submit(
          () -> {
            try {
              latch.await();
              String number =
                  ScopedValue.where(RequestScopes.TENANT_ID, schemaConc)
                      .call(
                          () ->
                              transactionTemplate.execute(
                                  tx -> invoiceNumberService.assignNumber()));
              results.add(number);
            } catch (Exception e) {
              errors.add(e);
            }
          });
    }

    // Release both threads at the same time
    latch.countDown();

    executor.shutdown();
    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    assertThat(errors).isEmpty();
    assertThat(results).hasSize(2);
    assertThat(Set.copyOf(results)).containsExactlyInAnyOrder("INV-0001", "INV-0002");
  }

  @Test
  void differentTenantsHaveIndependentSequences() {
    var resultX =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaIndepX)
            .call(() -> transactionTemplate.execute(tx -> invoiceNumberService.assignNumber()));
    var resultY =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaIndepY)
            .call(() -> transactionTemplate.execute(tx -> invoiceNumberService.assignNumber()));

    assertThat(resultX).isEqualTo("INV-0001");
    assertThat(resultY).isEqualTo("INV-0001");
  }

  // --- Helpers ---

  private String provisionAndGetSchema(String orgId, String orgName, String userPrefix)
      throws Exception {
    provisioningService.provisionTenant(orgId, orgName);
    planSyncService.syncPlan(orgId, "pro-plan");
    syncMember(orgId, userPrefix, userPrefix + "@test.com", orgName + " Owner", "owner");
    return orgSchemaMappingRepository.findByClerkOrgId(orgId).orElseThrow().getSchemaName();
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

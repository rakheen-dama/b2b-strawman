package io.b2mash.b2b.b2bstrawman.datarequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.LocalDate;
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
class DataSubjectRequestIntegrationTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_dsr_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private DataSubjectRequestRepository dsrRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "DSR Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");
    memberIdOwner =
        UUID.fromString(
            syncMember(ORG_ID, "user_dsr_owner", "dsr_owner@test.com", "DSR Owner", "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
  }

  @Test
  void shouldSaveAndRetrieveDataSubjectRequest() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      new Customer("DSR Customer", "dsr@test.com", null, null, null, memberIdOwner);
                  customer = customerRepository.save(customer);

                  var dsr =
                      new DataSubjectRequest(
                          customer.getId(),
                          "ACCESS",
                          "Request access to personal data",
                          LocalDate.of(2026, 3, 15),
                          memberIdOwner);
                  dsr = dsrRepository.save(dsr);

                  var found = dsrRepository.findOneById(dsr.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getRequestType()).isEqualTo("ACCESS");
                  assertThat(found.get().getStatus()).isEqualTo("RECEIVED");
                  assertThat(found.get().getDeadline()).isEqualTo(LocalDate.of(2026, 3, 15));
                }));
  }

  @Test
  void shouldCompleteDataSubjectRequest() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      new Customer(
                          "DSR Complete Customer",
                          "dsr_complete@test.com",
                          null,
                          null,
                          null,
                          memberIdOwner);
                  customer = customerRepository.save(customer);

                  var dsr =
                      new DataSubjectRequest(
                          customer.getId(),
                          "DELETION",
                          "Delete my data",
                          LocalDate.of(2026, 3, 20),
                          memberIdOwner);
                  dsr = dsrRepository.save(dsr);

                  dsr.complete(memberIdOwner, Instant.now());
                  dsr = dsrRepository.save(dsr);

                  var found = dsrRepository.findOneById(dsr.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getStatus()).isEqualTo("COMPLETED");
                  assertThat(found.get().getCompletedBy()).isEqualTo(memberIdOwner);
                  assertThat(found.get().getCompletedAt()).isNotNull();
                }));
  }

  @Test
  void shouldRejectDataSubjectRequest() {
    runInTenant(
        tenantSchema,
        ORG_ID,
        memberIdOwner,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      new Customer(
                          "DSR Reject Customer",
                          "dsr_reject@test.com",
                          null,
                          null,
                          null,
                          memberIdOwner);
                  customer = customerRepository.save(customer);

                  var dsr =
                      new DataSubjectRequest(
                          customer.getId(),
                          "OBJECTION",
                          "Object to processing",
                          LocalDate.of(2026, 3, 25),
                          memberIdOwner);
                  dsr = dsrRepository.save(dsr);

                  dsr.reject("Processing is necessary for legal obligation");
                  dsr = dsrRepository.save(dsr);

                  var found = dsrRepository.findOneById(dsr.getId());
                  assertThat(found).isPresent();
                  assertThat(found.get().getStatus()).isEqualTo("REJECTED");
                  assertThat(found.get().getRejectionReason())
                      .isEqualTo("Processing is necessary for legal obligation");
                }));
  }

  // --- Helpers ---

  private void runInTenant(String schema, String orgId, UUID memberId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, schema)
        .where(RequestScopes.ORG_ID, orgId)
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

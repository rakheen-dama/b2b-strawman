package io.b2mash.b2b.b2bstrawman.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstance;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistInstanceRepository;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplate;
import io.b2mash.b2b.b2bstrawman.checklist.ChecklistTemplateRepository;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.datarequest.DataSubjectRequest;
import io.b2mash.b2b.b2bstrawman.datarequest.DataSubjectRequestRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicy;
import io.b2mash.b2b.b2bstrawman.retention.RetentionPolicyRepository;
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

/**
 * Shared schema (Starter tier) isolation tests for compliance entities. Provisions two Starter orgs
 * sharing the tenant_shared schema and verifies that records are isolated via Hibernate @Filter and
 * tenant_id population.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplianceSharedSchemaTest {

  private static final String ORG_A_ID = "org_comp_shared_a";
  private static final String ORG_B_ID = "org_comp_shared_b";
  private static final String API_KEY = "test-api-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private ChecklistTemplateRepository templateRepository;
  @Autowired private ChecklistInstanceRepository instanceRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DataSubjectRequestRepository dsrRepository;
  @Autowired private RetentionPolicyRepository retentionPolicyRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private UUID memberAId;
  private UUID memberBId;

  @BeforeAll
  void provisionStarterOrgs() throws Exception {
    // Provision two Starter orgs (no plan sync = Starter tier = shared schema)
    provisioningService.provisionTenant(ORG_A_ID, "Compliance Shared A");
    provisioningService.provisionTenant(ORG_B_ID, "Compliance Shared B");

    memberAId =
        UUID.fromString(
            syncMember(
                ORG_A_ID,
                "user_comp_shared_a",
                "comp_shared_a@test.com",
                "Shared A User",
                "owner"));
    memberBId =
        UUID.fromString(
            syncMember(
                ORG_B_ID,
                "user_comp_shared_b",
                "comp_shared_b@test.com",
                "Shared B User",
                "owner"));
  }

  @Test
  void checklistTemplateRlsIsolatesByTenant() {
    var idHolder = new UUID[1];

    // Create template in Org A
    runInSharedTenant(
        ORG_A_ID,
        memberAId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ChecklistTemplate(
                          "Shared Template A",
                          "shared-template-a",
                          "Template in Org A",
                          "ANY",
                          "ORG_CUSTOM");
                  template = templateRepository.save(template);
                  idHolder[0] = template.getId();

                  // Verify tenant_id is populated
                  assertThat(template.getTenantId()).isEqualTo(ORG_A_ID);
                }));

    // Org B cannot see it via findOneById
    runInSharedTenant(
        ORG_B_ID,
        memberBId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = templateRepository.findOneById(idHolder[0]);
                  assertThat(found).isEmpty();
                }));
  }

  @Test
  void checklistInstanceRlsIsolates() {
    var instanceIdHolder = new UUID[1];

    runInSharedTenant(
        ORG_A_ID,
        memberAId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ChecklistTemplate(
                          "Instance Isolation Template",
                          "instance-isolation-tpl",
                          "For instance test",
                          "ANY",
                          "ORG_CUSTOM");
                  template = templateRepository.save(template);

                  var customer =
                      new Customer(
                          "Instance Isolation Customer",
                          "inst_iso@test.com",
                          null,
                          null,
                          null,
                          memberAId);
                  customer = customerRepository.save(customer);

                  var instance =
                      new ChecklistInstance(template.getId(), customer.getId(), "IN_PROGRESS");
                  instance = instanceRepository.save(instance);
                  instanceIdHolder[0] = instance.getId();

                  assertThat(instance.getTenantId()).isEqualTo(ORG_A_ID);
                }));

    runInSharedTenant(
        ORG_B_ID,
        memberBId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = instanceRepository.findOneById(instanceIdHolder[0]);
                  assertThat(found).isEmpty();
                }));
  }

  @Test
  void dataSubjectRequestRlsIsolates() {
    var dsrIdHolder = new UUID[1];

    runInSharedTenant(
        ORG_A_ID,
        memberAId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      new Customer(
                          "DSR Shared Customer",
                          "dsr_shared@test.com",
                          null,
                          null,
                          null,
                          memberAId);
                  customer = customerRepository.save(customer);

                  var dsr =
                      new DataSubjectRequest(
                          customer.getId(),
                          "ACCESS",
                          "Shared DSR test",
                          LocalDate.of(2026, 4, 1),
                          memberAId);
                  dsr = dsrRepository.save(dsr);
                  dsrIdHolder[0] = dsr.getId();

                  assertThat(dsr.getTenantId()).isEqualTo(ORG_A_ID);
                }));

    runInSharedTenant(
        ORG_B_ID,
        memberBId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = dsrRepository.findOneById(dsrIdHolder[0]);
                  assertThat(found).isEmpty();
                }));
  }

  @Test
  void retentionPolicyRlsIsolates() {
    var policyIdHolder = new UUID[1];

    runInSharedTenant(
        ORG_A_ID,
        memberAId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var policy = new RetentionPolicy("DOCUMENT", 365, "DOCUMENT_ARCHIVED", "FLAG");
                  policy = retentionPolicyRepository.save(policy);
                  policyIdHolder[0] = policy.getId();

                  assertThat(policy.getTenantId()).isEqualTo(ORG_A_ID);
                }));

    runInSharedTenant(
        ORG_B_ID,
        memberBId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = retentionPolicyRepository.findOneById(policyIdHolder[0]);
                  assertThat(found).isEmpty();
                }));
  }

  @Test
  void crossTenantFindOneByIdReturnsEmpty() {
    var templateIdHolder = new UUID[1];

    runInSharedTenant(
        ORG_A_ID,
        memberAId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new ChecklistTemplate(
                          "Cross Tenant Shared",
                          "cross-tenant-shared",
                          "Cross-tenant test",
                          "COMPANY",
                          "ORG_CUSTOM");
                  template = templateRepository.save(template);
                  templateIdHolder[0] = template.getId();
                }));

    // Org A can still find it
    runInSharedTenant(
        ORG_A_ID,
        memberAId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = templateRepository.findOneById(templateIdHolder[0]);
                  assertThat(found).isPresent();
                }));

    // Org B cannot
    runInSharedTenant(
        ORG_B_ID,
        memberBId,
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var found = templateRepository.findOneById(templateIdHolder[0]);
                  assertThat(found).isEmpty();
                }));
  }

  // --- Helpers ---

  private void runInSharedTenant(String orgId, UUID memberId, Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, "tenant_shared")
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

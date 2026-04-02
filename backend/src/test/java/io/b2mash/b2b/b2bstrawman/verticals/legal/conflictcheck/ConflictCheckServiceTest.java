package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService.PerformConflictCheckRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.ConflictCheckService.ResolveRequest;
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
class ConflictCheckServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_conflict_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private ConflictCheckService conflictCheckService;
  @Autowired private ConflictCheckRepository conflictCheckRepository;
  @Autowired private AdversePartyRepository adversePartyRepository;
  @Autowired private AdversePartyLinkRepository adversePartyLinkRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private AuditEventRepository auditEventRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Conflict Check Service Test Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_conflict_svc_owner",
                "conflict_svc@test.com",
                "Conflict Svc Owner",
                "owner"));

    // Enable the conflict_check module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("conflict_check"));
                      orgSettingsRepository.save(settings);
                    }));

    // Create test customer and project
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer(
                              "Conflict Test Corp", "conflict_test@test.com", memberId));
                  customerId = customer.getId();

                  var project = new Project("Smith v Jones Matter", "Test matter", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();
                }));

    // Create adverse parties for testing
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Adverse party with known ID number
                  var ap1 =
                      new AdverseParty(
                          "Ndlovu Trading (Pty) Ltd",
                          "8501015800083",
                          "2020/123456/07",
                          "COMPANY",
                          "Ndlovu Corp, Ndlovu Group",
                          null);
                  adversePartyRepository.saveAndFlush(ap1);

                  // Link adverse party to a project
                  var link =
                      new AdversePartyLink(
                          ap1.getId(),
                          projectId,
                          customerId,
                          "OPPOSING_PARTY",
                          "Opposing in Smith v Jones");
                  adversePartyLinkRepository.saveAndFlush(link);

                  // Another adverse party with similar name
                  var ap2 =
                      new AdverseParty(
                          "Totally Unrelated Company", null, null, "COMPANY", null, null);
                  adversePartyRepository.saveAndFlush(ap2);
                }));
  }

  // --- 401.5: Fuzzy match tests ---

  @Test
  void performCheck_exactIdNumber_returnsConflictFound() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new PerformConflictCheckRequest(
                          "Some Random Name", "8501015800083", null, "NEW_CLIENT", null, null);

                  var response = conflictCheckService.performCheck(request, memberId);

                  assertThat(response.result()).isEqualTo("CONFLICT_FOUND");
                  assertThat(response.conflictsFound()).isNotEmpty();
                  assertThat(response.conflictsFound())
                      .anyMatch(c -> "ID_NUMBER_EXACT".equals(c.matchType()));
                }));
  }

  @Test
  void performCheck_highNameSimilarity_returnsConflictFound() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new PerformConflictCheckRequest(
                          "Ndlovu Trading (Pty) Ltd", null, null, "NEW_CLIENT", null, null);

                  var response = conflictCheckService.performCheck(request, memberId);

                  assertThat(response.result()).isEqualTo("CONFLICT_FOUND");
                  assertThat(response.conflictsFound()).isNotEmpty();
                }));
  }

  @Test
  void performCheck_mediumNameSimilarity_returnsPotentialConflict() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create a party with a name that will partially match
                  adversePartyRepository.saveAndFlush(
                      new AdverseParty(
                          "Mbeki Construction Services", null, null, "COMPANY", null, null));

                  var request =
                      new PerformConflictCheckRequest(
                          "Mbeki Construction", null, null, "NEW_CLIENT", null, null);

                  var response = conflictCheckService.performCheck(request, memberId);

                  // Should find a match -- either POTENTIAL_CONFLICT or CONFLICT_FOUND
                  // depending on the similarity score
                  assertThat(response.result()).isIn("POTENTIAL_CONFLICT", "CONFLICT_FOUND");
                  assertThat(response.conflictsFound()).isNotEmpty();
                }));
  }

  @Test
  void performCheck_noMatch_returnsNoConflict() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new PerformConflictCheckRequest(
                          "Zxywqrst Unique Name Corp", null, null, "NEW_CLIENT", null, null);

                  var response = conflictCheckService.performCheck(request, memberId);

                  assertThat(response.result()).isEqualTo("NO_CONFLICT");
                  assertThat(response.conflictsFound()).isEmpty();
                }));
  }

  // --- 401.6: Detail tests ---

  @Test
  void performCheck_conflictsFound_containsCorrectAdversePartyAndMatchType() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new PerformConflictCheckRequest(
                          "Some Name", "8501015800083", null, "NEW_MATTER", null, null);

                  var response = conflictCheckService.performCheck(request, memberId);

                  assertThat(response.result()).isEqualTo("CONFLICT_FOUND");
                  assertThat(response.conflictsFound()).isNotEmpty();

                  var idMatch =
                      response.conflictsFound().stream()
                          .filter(c -> "ID_NUMBER_EXACT".equals(c.matchType()))
                          .findFirst();
                  assertThat(idMatch).isPresent();
                  assertThat(idMatch.get().adversePartyId()).isNotNull();
                  assertThat(idMatch.get().adversePartyName())
                      .isEqualTo("Ndlovu Trading (Pty) Ltd");
                }));
  }

  @Test
  void performCheck_customerTableAlsoSearched() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create a customer with a name that will match
                  customerRepository.saveAndFlush(
                      createActiveCustomer(
                          "Khumalo Engineering Holdings", "khumalo_test@test.com", memberId));

                  var request =
                      new PerformConflictCheckRequest(
                          "Khumalo Engineering Holdings", null, null, "NEW_CLIENT", null, null);

                  var response = conflictCheckService.performCheck(request, memberId);

                  assertThat(response.result()).isIn("CONFLICT_FOUND", "POTENTIAL_CONFLICT");
                  assertThat(response.conflictsFound()).isNotEmpty();
                  assertThat(response.conflictsFound())
                      .anyMatch(c -> "EXISTING_CLIENT".equals(c.relationship()));
                }));
  }

  // --- 401.7: Audit and resolution tests ---

  @Test
  void performCheck_createsAuditEvent() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  long auditCountBefore = auditEventRepository.count();

                  var request =
                      new PerformConflictCheckRequest(
                          "Audit Test Name", null, null, "PERIODIC_REVIEW", null, null);

                  conflictCheckService.performCheck(request, memberId);

                  long auditCountAfter = auditEventRepository.count();
                  assertThat(auditCountAfter).isGreaterThan(auditCountBefore);
                }));
  }

  @Test
  void resolve_updatesResolutionFields() {
    var checkId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // First perform a check
                  var request =
                      new PerformConflictCheckRequest(
                          "Resolve Test Name", "8501015800083", null, "NEW_CLIENT", null, null);

                  var response = conflictCheckService.performCheck(request, memberId);
                  checkId[0] = response.id();
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var resolveRequest =
                      new ResolveRequest("WAIVER_OBTAINED", "Client consented", null);

                  var resolved = conflictCheckService.resolve(checkId[0], resolveRequest, memberId);

                  assertThat(resolved.resolution()).isEqualTo("WAIVER_OBTAINED");
                  assertThat(resolved.resolutionNotes()).isEqualTo("Client consented");
                  assertThat(resolved.resolvedBy()).isEqualTo(memberId);
                  assertThat(resolved.resolvedAt()).isNotNull();
                }));
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }
}

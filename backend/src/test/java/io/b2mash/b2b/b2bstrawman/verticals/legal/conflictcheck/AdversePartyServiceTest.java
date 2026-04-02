package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ModuleNotEnabledException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.AdversePartyService.CreateAdversePartyRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.AdversePartyService.LinkRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdversePartyServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_adverse_svc_test";
  private static final String DISABLED_ORG_ID = "org_adverse_svc_disabled";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private AdversePartyService adversePartyService;
  @Autowired private AdversePartyRepository adversePartyRepository;
  @Autowired private AdversePartyLinkRepository adversePartyLinkRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;

  private String tenantSchema;
  private String disabledTenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;
  private UUID projectId2;

  @BeforeAll
  void setup() throws Exception {
    // Provision enabled tenant
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Adverse Party Service Test Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID,
                "user_adverse_svc_owner",
                "adverse_svc@test.com",
                "Adverse Svc Owner",
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

    // Create test customer and projects
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer("Adverse Test Corp", "adverse@test.com", memberId));
                  customerId = customer.getId();

                  var project = new Project("Smith v Jones", "Test matter", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();

                  var project2 = new Project("Doe v Roe", "Second matter", memberId);
                  project2.setCustomerId(customerId);
                  project2 = projectRepository.saveAndFlush(project2);
                  projectId2 = project2.getId();
                }));

    // Provision disabled tenant (no modules enabled)
    disabledTenantSchema =
        provisioningService
            .provisionTenant(DISABLED_ORG_ID, "Adverse Disabled Org", null)
            .schemaName();
    syncMember(
        DISABLED_ORG_ID,
        "user_adverse_svc_dis",
        "adverse_dis@test.com",
        "Adverse Disabled Owner",
        "owner");
  }

  @Test
  void create_savesAdverseParty() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateAdversePartyRequest(
                          "John Doe",
                          "8501015800083",
                          null,
                          "NATURAL_PERSON",
                          "Johnny D, JD",
                          "Known associate");

                  var response = adversePartyService.create(request);

                  assertThat(response.id()).isNotNull();
                  assertThat(response.name()).isEqualTo("John Doe");
                  assertThat(response.partyType()).isEqualTo("NATURAL_PERSON");
                  assertThat(response.idNumber()).isEqualTo("8501015800083");
                  assertThat(response.aliases()).isEqualTo("Johnny D, JD");
                  assertThat(response.linkedMatterCount()).isZero();
                }));
  }

  @Test
  void link_createsAdversePartyLinkRecord() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var party =
                      adversePartyRepository.save(
                          new AdverseParty(
                              "Link Test Corp", null, "2020/123456/07", "COMPANY", null, null));

                  var linkRequest =
                      new LinkRequest(projectId, customerId, "OPPOSING_PARTY", "Defendant");

                  var response = adversePartyService.link(party.getId(), linkRequest);

                  assertThat(response.id()).isNotNull();
                  assertThat(response.adversePartyId()).isEqualTo(party.getId());
                  assertThat(response.projectId()).isEqualTo(projectId);
                  assertThat(response.relationship()).isEqualTo("OPPOSING_PARTY");
                  assertThat(response.adversePartyName()).isEqualTo("Link Test Corp");
                  assertThat(response.projectName()).isEqualTo("Smith v Jones");
                }));
  }

  @Test
  void link_duplicateSamePartyAndProject_throwsConflict() {
    // Setup: create party and first link in one transaction
    var partyId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var party =
                      adversePartyRepository.save(
                          new AdverseParty("Dup Link Corp", null, null, "COMPANY", null, null));
                  partyId[0] = party.getId();

                  var linkRequest =
                      new LinkRequest(projectId, customerId, "OPPOSING_PARTY", "First link");
                  adversePartyService.link(party.getId(), linkRequest);
                }));

    // Attempt duplicate in a separate transaction
    runInTenant(
        () -> {
          var duplicateRequest = new LinkRequest(projectId, customerId, "WITNESS", "Duplicate");

          assertThatThrownBy(() -> adversePartyService.link(partyId[0], duplicateRequest))
              .isInstanceOf(ResourceConflictException.class);
        });
  }

  @Test
  void delete_failsWhenActiveLinksExist() {
    // Setup: create party and link in one transaction
    var partyId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var party =
                      adversePartyRepository.save(
                          new AdverseParty(
                              "Delete Protected Corp", null, null, "COMPANY", null, null));
                  partyId[0] = party.getId();

                  adversePartyLinkRepository.save(
                      new AdversePartyLink(
                          party.getId(),
                          projectId2,
                          customerId,
                          "RELATED_ENTITY",
                          "Testing delete protection"));
                }));

    // Attempt delete in separate scope (no outer transaction to conflict)
    runInTenant(
        () ->
            assertThatThrownBy(() -> adversePartyService.delete(partyId[0]))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("active links"));
  }

  @Test
  void delete_succeedsWhenNoLinks() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var party =
                      adversePartyRepository.save(
                          new AdverseParty("Delete Me Corp", null, null, "COMPANY", null, null));

                  adversePartyService.delete(party.getId());

                  assertThat(adversePartyRepository.findById(party.getId())).isEmpty();
                }));
  }

  @Test
  void fuzzyNameSearch_returnsPartiesAboveThreshold() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  adversePartyRepository.save(
                      new AdverseParty(
                          "Acme Holdings (Pty) Ltd", null, null, "COMPANY", null, null));
                  adversePartyRepository.save(
                      new AdverseParty(
                          "Totally Different Name", null, null, "NATURAL_PERSON", null, null));

                  var results = adversePartyService.list("Acme Holdings", null, Pageable.unpaged());

                  assertThat(results.getContent())
                      .anyMatch(r -> r.name().contains("Acme Holdings"));
                  assertThat(results.getContent())
                      .noneMatch(r -> r.name().equals("Totally Different Name"));
                }));
  }

  @Test
  void create_throwsModuleNotEnabled_whenModuleDisabled() {
    ScopedValue.where(RequestScopes.TENANT_ID, disabledTenantSchema)
        .where(RequestScopes.ORG_ID, DISABLED_ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var request =
                  new CreateAdversePartyRequest(
                      "Should Fail", null, null, "NATURAL_PERSON", null, null);

              assertThatThrownBy(() -> adversePartyService.create(request))
                  .isInstanceOf(ModuleNotEnabledException.class);
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
                        {"clerkOrgId":"%s","clerkUserId":"%s","email":"%s","name":"%s","avatarUrl":null,"orgRole":"%s"}
                        """
                            .formatted(orgId, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return com.jayway.jsonpath.JsonPath.read(
        result.getResponse().getContentAsString(), "$.memberId");
  }
}

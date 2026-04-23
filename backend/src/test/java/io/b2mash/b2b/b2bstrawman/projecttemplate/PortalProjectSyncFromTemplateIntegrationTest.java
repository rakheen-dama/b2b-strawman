package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.InstantiateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GAP-P-03 regression guard. Verifies that a matter created through {@link
 * ProjectTemplateService#instantiateTemplate} publishes {@code CustomerProjectLinkedEvent} so the
 * portal read-model (portal.portal_projects) is populated. Without the event, a portal contact
 * linked to the matter's customer sees "No projects yet" and a direct-URL GET returns 404 — this
 * blocks the Sipho Dlamini portal view in the Legal ZA lifecycle.
 *
 * <p>Also guards against authorization widening: a portal contact belonging to a DIFFERENT customer
 * must NOT see projects owned by the first customer (Day 15 isolation probe).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalProjectSyncFromTemplateIntegrationTest {

  private static final String ORG_ID = "org_portal_sync_p03";
  private static final String ORG_NAME = "Portal Sync P03 Test Org";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private ProjectTemplateService templateService;
  @Autowired private ProjectTemplateRepository templateRepository;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private PortalReadModelRepository readModelRepo;
  @Autowired private PlatformTransactionManager transactionManager;

  private String tenantSchema;
  private UUID memberId;
  private TransactionTemplate tenantTxTemplate;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, ORG_NAME, null);
    memberId = UUID.fromString(TestMemberHelper.syncOwner(mockMvc, ORG_ID, "portal_sync_p03"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    tenantTxTemplate = new TransactionTemplate(transactionManager);
  }

  /**
   * Reproduces the Sipho Dlamini blocker: a portal contact linked to a PROSPECT customer should see
   * matters owned by that customer even when the matter is created via a template. Before the fix,
   * {@code instantiateTemplate} saved the {@code CustomerProject} link but did not publish {@code
   * CustomerProjectLinkedEvent}, so the portal read-model stayed empty and the portal rendered "No
   * projects yet".
   */
  @Test
  void portalContact_sees_matter_created_from_template_even_when_customer_is_prospect()
      throws Exception {
    UUID[] customerIdHolder = new UUID[1];
    UUID[] projectIdHolder = new UUID[1];

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      // Customer stays in default PROSPECT lifecycle — we never call transition.
                      var customer =
                          customerService.createCustomer(
                              "Sipho Dlamini",
                              "sipho.dlamini+p03@test.com",
                              null,
                              null,
                              null,
                              memberId);
                      customerIdHolder[0] = customer.getId();

                      portalContactService.createContact(
                          ORG_ID,
                          customer.getId(),
                          "sipho.portal+p03@test.com",
                          "Sipho Portal",
                          PortalContact.ContactRole.PRIMARY);

                      var template =
                          templateRepository.saveAndFlush(
                              new ProjectTemplate(
                                  "Template P03",
                                  "{customer} - matter",
                                  null,
                                  true,
                                  "MANUAL",
                                  null,
                                  memberId));

                      var request =
                          new InstantiateTemplateRequest(
                              "Sipho Matter P03",
                              customer.getId(),
                              null,
                              "Portal sync regression matter",
                              null,
                              null,
                              null);
                      var project =
                          templateService.instantiateTemplate(template.getId(), request, memberId);
                      projectIdHolder[0] = project.getId();
                    }));

    // AFTER_COMMIT event listener fires here -> portal.portal_projects is populated
    var customerId = customerIdHolder[0];
    var projectId = projectIdHolder[0];

    var projects = readModelRepo.findProjectsByCustomer(ORG_ID, customerId);
    assertThat(projects)
        .as("Portal read-model must contain the matter created via template (GAP-P-03)")
        .extracting("id")
        .contains(projectId);

    // Verify via the HTTP endpoint the portal UI calls -- uses the same token the customer sees.
    String portalToken = portalJwtService.issueToken(customerId, ORG_ID);

    mockMvc
        .perform(get("/portal/projects").header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + projectId + "')]").exists())
        .andExpect(jsonPath("$[?(@.id == '" + projectId + "')].name").value("Sipho Matter P03"));

    mockMvc
        .perform(
            get("/portal/projects/{id}", projectId)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(projectId.toString()))
        .andExpect(jsonPath("$.name").value("Sipho Matter P03"));
  }

  /**
   * Isolation regression guard (Day 15 probe). A portal contact linked to customer B must NOT see
   * matters owned by customer A, even after the P-03 read-model sync is enabled. This guards
   * against accidentally widening portal authorization when fixing P-03.
   */
  @Test
  void portalContact_for_other_customer_cannot_see_first_customers_matter() throws Exception {
    UUID[] customerAId = new UUID[1];
    UUID[] customerBId = new UUID[1];
    UUID[] matterAId = new UUID[1];

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      var customerA =
                          customerService.createCustomer(
                              "Customer A Isolation",
                              "a.isolation+p03@test.com",
                              null,
                              null,
                              null,
                              memberId);
                      customerAId[0] = customerA.getId();

                      var customerB =
                          customerService.createCustomer(
                              "Customer B Isolation",
                              "b.isolation+p03@test.com",
                              null,
                              null,
                              null,
                              memberId);
                      customerBId[0] = customerB.getId();

                      // Portal contact belongs to customer B (different from matter owner).
                      portalContactService.createContact(
                          ORG_ID,
                          customerB.getId(),
                          "b.portal+p03@test.com",
                          "Customer B Portal",
                          PortalContact.ContactRole.PRIMARY);

                      var template =
                          templateRepository.saveAndFlush(
                              new ProjectTemplate(
                                  "Isolation Template P03",
                                  "{customer}",
                                  null,
                                  true,
                                  "MANUAL",
                                  null,
                                  memberId));

                      var request =
                          new InstantiateTemplateRequest(
                              "Customer A Matter P03",
                              customerA.getId(),
                              null,
                              null,
                              null,
                              null,
                              null);
                      var project =
                          templateService.instantiateTemplate(template.getId(), request, memberId);
                      matterAId[0] = project.getId();
                    }));

    // Token issued for customer B — must NOT reveal customer A's matter.
    String customerBToken = portalJwtService.issueToken(customerBId[0], ORG_ID);

    mockMvc
        .perform(get("/portal/projects").header("Authorization", "Bearer " + customerBToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + matterAId[0] + "')]").doesNotExist());

    mockMvc
        .perform(
            get("/portal/projects/{id}", matterAId[0])
                .header("Authorization", "Bearer " + customerBToken))
        .andExpect(status().isNotFound());

    // Sanity: the read-model row exists for customer A (not B) — not leaking to customer B.
    var forB = readModelRepo.findProjectsByCustomer(ORG_ID, customerBId[0]);
    assertThat(forB).extracting("id").doesNotContain(matterAId[0]);
  }

  /**
   * Scheduler-path regression guard (CodeRabbit follow-up to GAP-P-03). The recurring-schedule
   * executor calls {@link ProjectTemplateService#instantiateFromTemplate} after binding TENANT_ID +
   * ORG_ID as ScopedValues. This test verifies that when the scheduler passes explicit {@code
   * tenantIdOverride} and {@code orgIdOverride} values, the downstream {@code
   * CustomerProjectLinkedEvent} carries those stable ids so the portal read-model projects the
   * matter correctly — even when the caller's RequestScopes are not the source of truth for the
   * event payload.
   *
   * <p>To exercise the "outside standard request scope" path, the creation of template + customer
   * happens in a normal request-scoped transaction (required for JPA multitenancy), but {@link
   * ProjectTemplateService#instantiateFromTemplate} is then invoked directly with the tenant/org
   * override parameters the scheduler uses. The resulting portal row must match.
   */
  @Test
  void schedulerPath_instantiateFromTemplate_populates_portal_read_model_via_explicit_overrides()
      throws Exception {
    UUID[] customerIdHolder = new UUID[1];
    UUID[] templateIdHolder = new UUID[1];

    // 1. Create customer + template + portal contact in a normal tenant-scoped transaction.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      var customer =
                          customerService.createCustomer(
                              "Scheduler Customer P03",
                              "scheduler.customer+p03@test.com",
                              null,
                              null,
                              null,
                              memberId);
                      customerIdHolder[0] = customer.getId();

                      portalContactService.createContact(
                          ORG_ID,
                          customer.getId(),
                          "scheduler.portal+p03@test.com",
                          "Scheduler Portal",
                          PortalContact.ContactRole.PRIMARY);

                      var template =
                          templateRepository.saveAndFlush(
                              new ProjectTemplate(
                                  "Scheduler Template P03",
                                  "{customer} - scheduled matter",
                                  null,
                                  true,
                                  "RECURRING_SCHEDULE",
                                  null,
                                  memberId));
                      templateIdHolder[0] = template.getId();
                    }));

    // 2. Invoke the scheduler-style instantiateFromTemplate directly. TENANT_ID is still
    //    bound (for JPA search_path), but the event payload MUST be populated from the
    //    explicit overrides, mirroring RecurringScheduleService's call.
    UUID[] projectIdHolder = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () ->
                tenantTxTemplate.executeWithoutResult(
                    status -> {
                      var customer = customerService.getCustomer(customerIdHolder[0]);
                      var template =
                          templateRepository
                              .findById(templateIdHolder[0])
                              .orElseThrow(
                                  () -> new IllegalStateException("Template not found in setup"));

                      var project =
                          templateService.instantiateFromTemplate(
                              template,
                              "Scheduler Matter P03",
                              customer,
                              /* projectLeadMemberId */ null,
                              /* actingMemberId */ memberId,
                              /* tenantIdOverride */ tenantSchema,
                              /* orgIdOverride */ ORG_ID);
                      projectIdHolder[0] = project.getId();
                    }));

    // 3. AFTER_COMMIT listener fires here — portal.portal_projects row must exist and be
    //    attributed to the org id we passed as the override.
    var customerId = customerIdHolder[0];
    var projectId = projectIdHolder[0];

    var projects = readModelRepo.findProjectsByCustomer(ORG_ID, customerId);
    assertThat(projects)
        .as(
            "Portal read-model must contain the matter created via the scheduler-style"
                + " instantiateFromTemplate (CodeRabbit follow-up to GAP-P-03)")
        .extracting("id")
        .contains(projectId);

    // 4. End-to-end: the portal HTTP endpoint returns the same matter.
    String portalToken = portalJwtService.issueToken(customerId, ORG_ID);
    mockMvc
        .perform(get("/portal/projects").header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + projectId + "')]").exists())
        .andExpect(
            jsonPath("$[?(@.id == '" + projectId + "')].name").value("Scheduler Matter P03"));
  }
}

package io.b2mash.b2b.b2bstrawman.billingrun;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProject;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.PlanSyncService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreement;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreementRepository;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerFrequency;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerPeriod;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerPeriodRepository;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerType;
import io.b2mash.b2b.b2bstrawman.retainer.RolloverPolicy;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BillingRunRetainerTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_billing_retainer_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private PlanSyncService planSyncService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CustomerProjectRepository customerProjectRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private RetainerAgreementRepository retainerAgreementRepository;
  @Autowired private RetainerPeriodRepository retainerPeriodRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private BillingRunItemRepository billingRunItemRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID customerId;
  private UUID customerId2;
  private UUID agreementId;
  private UUID agreementIdTerminated;

  @BeforeAll
  void setUp() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Retainer Test Org");
    planSyncService.syncPlan(ORG_ID, "pro-plan");

    memberIdOwner =
        UUID.fromString(syncMember("user_ret_owner", "ret_owner@test.com", "Ret Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      // Ensure OrgSettings exist with default currency
                      var settings = orgSettingsRepository.findForCurrentTenant().orElse(null);
                      if (settings == null) {
                        orgSettingsRepository.save(new OrgSettings("ZAR"));
                      }

                      // Customer 1 — with prerequisite fields for invoice generation
                      var customer =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "Retainer Corp", "retainer@test.com", memberIdOwner);
                      customer = customerRepository.save(customer);
                      customerId = customer.getId();

                      // Customer 2
                      var customer2 =
                          TestCustomerFactory.createActiveCustomerWithPrerequisiteFields(
                              "No Retainer Corp", "noretainer@test.com", memberIdOwner);
                      customer2 = customerRepository.save(customer2);
                      customerId2 = customer2.getId();

                      // Project linked to customer 1
                      var project = new Project("Ret Project", "Test project", memberIdOwner);
                      project = projectRepository.save(project);
                      customerProjectRepository.save(
                          new CustomerProject(customerId, project.getId(), memberIdOwner));

                      // ACTIVE retainer agreement with OPEN period ending in March 2026
                      var agreement =
                          new RetainerAgreement(
                              customerId,
                              "Monthly Retainer",
                              RetainerType.FIXED_FEE,
                              RetainerFrequency.MONTHLY,
                              LocalDate.of(2026, 2, 1),
                              null, // no end date
                              null, // no allocated hours (FIXED_FEE)
                              new BigDecimal("10000.00"),
                              RolloverPolicy.FORFEIT,
                              null,
                              null,
                              memberIdOwner);
                      agreement = retainerAgreementRepository.save(agreement);
                      agreementId = agreement.getId();

                      // Open period ending 2026-03-01 (within our billing period)
                      var period =
                          new RetainerPeriod(
                              agreementId,
                              LocalDate.of(2026, 2, 1),
                              LocalDate.of(2026, 3, 1),
                              null, // no allocated hours (FIXED_FEE)
                              null,
                              BigDecimal.ZERO);
                      retainerPeriodRepository.save(period);

                      // TERMINATED agreement — should NOT show in preview
                      var terminatedAgreement =
                          new RetainerAgreement(
                              customerId2,
                              "Terminated Retainer",
                              RetainerType.FIXED_FEE,
                              RetainerFrequency.MONTHLY,
                              LocalDate.of(2026, 1, 1),
                              null,
                              null,
                              new BigDecimal("5000.00"),
                              RolloverPolicy.FORFEIT,
                              null,
                              null,
                              memberIdOwner);
                      terminatedAgreement.terminate();
                      terminatedAgreement = retainerAgreementRepository.save(terminatedAgreement);
                      agreementIdTerminated = terminatedAgreement.getId();

                      // Open period for terminated agreement
                      var terminatedPeriod =
                          new RetainerPeriod(
                              agreementIdTerminated,
                              LocalDate.of(2026, 2, 1),
                              LocalDate.of(2026, 3, 1),
                              null,
                              null,
                              BigDecimal.ZERO);
                      retainerPeriodRepository.save(terminatedPeriod);
                    }));
  }

  @Test
  @Order(1)
  void retainerPreview_findsDueAgreements() throws Exception {
    String runId = createBillingRunWithRetainers("Retainer Run 1");

    mockMvc
        .perform(get("/api/billing-runs/" + runId + "/retainer-preview").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].agreementId").value(agreementId.toString()))
        .andExpect(jsonPath("$[0].customerName").value("Retainer Corp"))
        .andExpect(jsonPath("$[0].estimatedAmount").value(10000.00));
  }

  @Test
  @Order(2)
  void retainerPreview_excludesNonActive() throws Exception {
    String runId = createBillingRunWithRetainers("Retainer Run 2");

    var result =
        mockMvc
            .perform(get("/api/billing-runs/" + runId + "/retainer-preview").with(ownerJwt()))
            .andExpect(status().isOk())
            .andReturn();

    // Should only find the ACTIVE agreement, not the TERMINATED one
    String content = result.getResponse().getContentAsString();
    List<String> agreementIds = JsonPath.read(content, "$[*].agreementId");
    org.assertj.core.api.Assertions.assertThat(agreementIds)
        .contains(agreementId.toString())
        .doesNotContain(agreementIdTerminated.toString());
  }

  @Test
  @Order(3)
  void retainerGenerate_closesPeriodsAndCreatesInvoices() throws Exception {
    // Need a fresh agreement+period for generation (close consumes the period)
    final UUID[] freshAgreementId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var agreement =
                          new RetainerAgreement(
                              customerId,
                              "Gen Retainer",
                              RetainerType.FIXED_FEE,
                              RetainerFrequency.MONTHLY,
                              LocalDate.of(2026, 2, 1),
                              null,
                              null,
                              new BigDecimal("8000.00"),
                              RolloverPolicy.FORFEIT,
                              null,
                              null,
                              memberIdOwner);
                      agreement = retainerAgreementRepository.save(agreement);
                      freshAgreementId[0] = agreement.getId();

                      var period =
                          new RetainerPeriod(
                              agreement.getId(),
                              LocalDate.of(2026, 2, 1),
                              LocalDate.of(2026, 3, 1),
                              null,
                              null,
                              BigDecimal.ZERO);
                      retainerPeriodRepository.save(period);
                    }));

    String runId = createBillingRunWithRetainers("Retainer Run 3");

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/retainer-generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "retainerAgreementIds": ["%s"] }
                    """
                        .formatted(freshAgreementId[0])))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("GENERATED"))
        .andExpect(jsonPath("$[0].invoiceId").isNotEmpty());
  }

  @Test
  @Order(4)
  void retainerGenerate_linksToRun() throws Exception {
    // Create a fresh agreement+period
    final UUID[] freshAgreementId = new UUID[1];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var agreement =
                          new RetainerAgreement(
                              customerId,
                              "Link Retainer",
                              RetainerType.FIXED_FEE,
                              RetainerFrequency.MONTHLY,
                              LocalDate.of(2026, 2, 1),
                              null,
                              null,
                              new BigDecimal("6000.00"),
                              RolloverPolicy.FORFEIT,
                              null,
                              null,
                              memberIdOwner);
                      agreement = retainerAgreementRepository.save(agreement);
                      freshAgreementId[0] = agreement.getId();

                      var period =
                          new RetainerPeriod(
                              agreement.getId(),
                              LocalDate.of(2026, 2, 1),
                              LocalDate.of(2026, 3, 1),
                              null,
                              null,
                              BigDecimal.ZERO);
                      retainerPeriodRepository.save(period);
                    }));

    String runId = createBillingRunWithRetainers("Retainer Run 4");

    var result =
        mockMvc
            .perform(
                post("/api/billing-runs/" + runId + "/retainer-generate")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        { "retainerAgreementIds": ["%s"] }
                        """
                            .formatted(freshAgreementId[0])))
            .andExpect(status().isOk())
            .andReturn();

    String invoiceId = JsonPath.read(result.getResponse().getContentAsString(), "$[0].invoiceId");

    // Verify invoice is linked to billing run
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var invoice =
                          invoiceRepository.findById(UUID.fromString(invoiceId)).orElseThrow();
                      org.assertj.core.api.Assertions.assertThat(invoice.getBillingRunId())
                          .isEqualTo(UUID.fromString(runId));
                    }));
  }

  @Test
  @Order(5)
  void retainerGenerate_capturesFailures() throws Exception {
    // Use a non-existent agreement ID to trigger a failure
    UUID nonExistentId = UUID.randomUUID();
    String runId = createBillingRunWithRetainers("Retainer Run 5");

    mockMvc
        .perform(
            post("/api/billing-runs/" + runId + "/retainer-generate")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    { "retainerAgreementIds": ["%s"] }
                    """
                        .formatted(nonExistentId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("FAILED"))
        .andExpect(jsonPath("$[0].failureReason").isNotEmpty());
  }

  // --- Helpers ---

  private String createBillingRunWithRetainers(String name) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/billing-runs")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "%s",
                          "periodFrom": "2026-02-01",
                          "periodTo": "2026-03-31",
                          "currency": "ZAR",
                          "includeExpenses": false,
                          "includeRetainers": true
                        }
                        """
                            .formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private String syncMember(String clerkUserId, String email, String name, String orgRole)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/internal/members/sync")
                    .header("X-API-KEY", API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        { "clerkOrgId": "%s", "clerkUserId": "%s", "email": "%s", "name": "%s",
                          "avatarUrl": null, "orgRole": "%s" }
                        """
                            .formatted(ORG_ID, clerkUserId, email, name, orgRole)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.memberId");
  }

  private JwtRequestPostProcessor ownerJwt() {
    return jwt()
        .jwt(j -> j.subject("user_ret_owner").claim("o", Map.of("id", ORG_ID, "rol", "owner")))
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORG_OWNER")));
  }
}

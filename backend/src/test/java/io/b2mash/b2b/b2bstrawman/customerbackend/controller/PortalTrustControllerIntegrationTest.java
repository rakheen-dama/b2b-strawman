package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerService;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalTrustReadModelRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for {@link PortalTrustController}. Covers the module gate, per-customer
 * scoping, pagination and the {@code from}/{@code to} occurrence-time filter.
 *
 * <p>The module gate (legal-za vs accounting-za) is asserted via a {@link VerticalModuleGuard} spy
 * — provisioning a second tenant for the disabled case would double test time with no extra
 * coverage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalTrustControllerIntegrationTest {

  private static final String ORG_ID = "org_portal_trust_ctrl_test";
  private static final String TRUST_MODULE = "trust_accounting";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerService customerService;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PortalTrustReadModelRepository portalTrustRepo;
  @Autowired private ProjectRepository projectRepository;

  /**
   * The gate is toggled per-test via {@link VerticalModuleGuard#isModuleEnabled(String)}. Using a
   * spy keeps the underlying {@code OrgSettings}-backed logic intact for any collaborators that do
   * not override the stub, while letting the controller tests flip the answer explicitly.
   */
  @MockitoBean private VerticalModuleGuard moduleGuard;

  private String portalToken;
  private UUID customerId;
  private UUID matterA;
  private UUID matterB;
  private UUID otherCustomerId;
  private UUID otherCustomerMatter;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Portal Trust Ctrl Test Org", "legal-za");
    UUID memberId =
        UUID.fromString(TestMemberHelper.syncOwner(mockMvc, ORG_ID, "portal_trust_ctrl"));
    String tenantSchema = orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).get().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .run(
            () -> {
              var customer =
                  customerService.createCustomer(
                      "Trust Portal Customer", "trust-portal@test.com", null, null, null, memberId);
              customerId = customer.getId();
              portalContactService.createContact(
                  ORG_ID,
                  customerId,
                  "trust-portal-contact@test.com",
                  "Trust Portal Contact",
                  PortalContact.ContactRole.PRIMARY);

              var otherCustomer =
                  customerService.createCustomer(
                      "Other Trust Customer", "other-trust@test.com", null, null, null, memberId);
              otherCustomerId = otherCustomer.getId();
            });

    portalToken = portalJwtService.issueToken(customerId, ORG_ID);

    // --- Seed portal read-model directly — controller tests do not replay firm-side events. ---
    // Persist firm-side Project rows first so the cross-matter movements feed (GAP-L-68) can
    // resolve matter names. matterA/matterB take the projects' generated UUIDs; the per-matter
    // and summary endpoints don't care about Project rows — they were happy with bare matter
    // UUIDs in the read model — but the union-feed enriches matterName via ProjectRepository.
    UUID memberIdForProjects = memberId;
    UUID[] matterIds = new UUID[2];
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdForProjects)
        .run(
            () -> {
              var projectA = new Project("Matter A", "Matter A desc", memberIdForProjects);
              projectA.setCustomerId(customerId);
              projectRepository.save(projectA);
              matterIds[0] = projectA.getId();
              var projectB = new Project("Matter B", "Matter B desc", memberIdForProjects);
              projectB.setCustomerId(customerId);
              projectRepository.save(projectB);
              matterIds[1] = projectB.getId();
            });
    matterA = matterIds[0];
    matterB = matterIds[1];
    otherCustomerMatter = UUID.randomUUID();

    Instant lastTxnA = Instant.parse("2026-03-15T10:00:00Z");
    Instant lastTxnB = Instant.parse("2026-03-20T12:00:00Z");

    portalTrustRepo.upsertBalance(customerId, matterA, new BigDecimal("1500.00"), lastTxnA);
    portalTrustRepo.upsertBalance(customerId, matterB, new BigDecimal("250.00"), lastTxnB);
    portalTrustRepo.upsertBalance(
        otherCustomerId,
        otherCustomerMatter,
        new BigDecimal("9999.00"),
        Instant.parse("2026-03-10T09:00:00Z"));

    // matterA: 15 transactions spanning three calendar months (Jan / Feb / Mar) for pagination +
    // from/to filter scenarios. Transactions 0..4 → 2026-01, 5..9 → 2026-02, 10..14 → 2026-03.
    Instant janStart = Instant.parse("2026-01-05T08:00:00Z");
    Instant febStart = Instant.parse("2026-02-05T08:00:00Z");
    Instant marStart = Instant.parse("2026-03-05T08:00:00Z");
    for (int i = 0; i < 5; i++) {
      portalTrustRepo.upsertTransaction(
          UUID.randomUUID(),
          customerId,
          matterA,
          "DEPOSIT",
          new BigDecimal("100.00"),
          new BigDecimal("100.00").multiply(BigDecimal.valueOf(i + 1L)),
          janStart.plus(i, ChronoUnit.DAYS),
          "Jan deposit " + i,
          "REF-JAN-" + i);
    }
    for (int i = 0; i < 5; i++) {
      portalTrustRepo.upsertTransaction(
          UUID.randomUUID(),
          customerId,
          matterA,
          "DEPOSIT",
          new BigDecimal("100.00"),
          new BigDecimal("100.00").multiply(BigDecimal.valueOf(i + 6L)),
          febStart.plus(i, ChronoUnit.DAYS),
          "Feb deposit " + i,
          "REF-FEB-" + i);
    }
    for (int i = 0; i < 5; i++) {
      portalTrustRepo.upsertTransaction(
          UUID.randomUUID(),
          customerId,
          matterA,
          "DEPOSIT",
          new BigDecimal("100.00"),
          new BigDecimal("100.00").multiply(BigDecimal.valueOf(i + 11L)),
          marStart.plus(i, ChronoUnit.DAYS),
          "Mar deposit " + i,
          "REF-MAR-" + i);
    }

    // matterB: a single txn so the summary endpoint can assert two matters.
    portalTrustRepo.upsertTransaction(
        UUID.randomUUID(),
        customerId,
        matterB,
        "DEPOSIT",
        new BigDecimal("250.00"),
        new BigDecimal("250.00"),
        lastTxnB,
        "Matter B seed",
        "REF-B-0");

    // Default spy behaviour — trust module enabled. Individual tests override when needed.
    when(moduleGuard.isModuleEnabled(TRUST_MODULE)).thenReturn(true);
    when(moduleGuard.getEnabledModules()).thenReturn(Set.of(TRUST_MODULE));
  }

  // ==========================================================================
  // Scenario 1 — legal-za portal contact sees their matters only
  // ==========================================================================

  @Test
  void summary_returns_only_the_authenticated_customers_matters() throws Exception {
    when(moduleGuard.isModuleEnabled(TRUST_MODULE)).thenReturn(true);

    mockMvc
        .perform(get("/portal/trust/summary").header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matters.length()").value(2))
        .andExpect(jsonPath("$.matters[?(@.matterId == '" + matterA + "')]").exists())
        .andExpect(jsonPath("$.matters[?(@.matterId == '" + matterB + "')]").exists())
        .andExpect(
            jsonPath("$.matters[?(@.matterId == '" + otherCustomerMatter + "')]").doesNotExist());
  }

  // ==========================================================================
  // Scenario 2 — module disabled (e.g., accounting-za tenant) → 404
  // ==========================================================================

  @Test
  void summary_returns_404_when_trust_module_is_disabled() throws Exception {
    when(moduleGuard.isModuleEnabled(anyString())).thenReturn(false);

    // The service throws ResourceNotFoundException.withDetail("Trust ledger not available",
    // "The trust ledger is not available for this organization") — those strings land in the
    // ProblemDetail title/detail via ProblemDetailFactory. Lock the body shape down so a
    // future tweak to the message goes through a test update rather than silently changing
    // the contract the portal UI parses.
    mockMvc
        .perform(get("/portal/trust/summary").header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Trust ledger not available"))
        .andExpect(
            jsonPath("$.detail").value("The trust ledger is not available for this organization"));

    // Restore default for any tests that depend on the enabled state.
    when(moduleGuard.isModuleEnabled(TRUST_MODULE)).thenReturn(true);
  }

  // ==========================================================================
  // Scenario 3 — pagination returns size=10 rows plus a next page hint
  // ==========================================================================

  @Test
  void transactions_pagination_returns_page_size_and_exposes_next_page() throws Exception {
    when(moduleGuard.isModuleEnabled(TRUST_MODULE)).thenReturn(true);

    mockMvc
        .perform(
            get("/portal/trust/matters/{matterId}/transactions", matterA)
                .param("page", "0")
                .param("size", "10")
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(10))
        .andExpect(jsonPath("$.page.totalElements").value(15))
        .andExpect(jsonPath("$.page.totalPages").value(2))
        .andExpect(jsonPath("$.page.size").value(10))
        .andExpect(jsonPath("$.page.number").value(0));
  }

  // ==========================================================================
  // Scenario 4 — from/to filter excludes out-of-range transactions
  // ==========================================================================

  @Test
  void transactions_from_to_filter_excludes_out_of_range_rows() throws Exception {
    when(moduleGuard.isModuleEnabled(TRUST_MODULE)).thenReturn(true);

    String from = "2026-02-01T00:00:00Z";
    String to = "2026-02-28T23:59:59Z";

    mockMvc
        .perform(
            get("/portal/trust/matters/{matterId}/transactions", matterA)
                .param("page", "0")
                .param("size", "50")
                .param("from", from)
                .param("to", to)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(5))
        .andExpect(jsonPath("$.page.totalElements").value(5))
        .andExpect(jsonPath("$.content[?(@.reference =~ /REF-JAN-.*/)]").doesNotExist())
        .andExpect(jsonPath("$.content[?(@.reference =~ /REF-MAR-.*/)]").doesNotExist())
        .andExpect(jsonPath("$.content[?(@.reference =~ /REF-FEB-.*/)]").exists());
  }

  // ==========================================================================
  // Scenario 5 (GAP-L-55) — unknown matter yields a "Project not found" error so the portal
  // message matches sibling portal endpoints instead of drifting into legal-firm "Matter" wording.
  // ==========================================================================

  @Test
  void transactions_returns_project_not_found_wording_for_unknown_matter() throws Exception {
    when(moduleGuard.isModuleEnabled(TRUST_MODULE)).thenReturn(true);

    UUID unknownMatter = UUID.randomUUID();

    mockMvc
        .perform(
            get("/portal/trust/matters/{matterId}/transactions", unknownMatter)
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("Project not found"));
  }

  // ==========================================================================
  // Scenario 6 (GAP-L-68) — cross-matter movements feed for the home tile.
  // ==========================================================================

  @Test
  void movements_default_limit_caps_at_ten_rows() throws Exception {
    when(moduleGuard.isModuleEnabled(TRUST_MODULE)).thenReturn(true);

    // 16 transactions exist for this customer (15 on matterA, 1 on matterB). Default limit = 10.
    mockMvc
        .perform(get("/portal/trust/movements").header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(10));
  }

  @Test
  void movements_returns_at_most_limit_rows_ordered_newest_first() throws Exception {
    when(moduleGuard.isModuleEnabled(TRUST_MODULE)).thenReturn(true);

    // Most recent transaction overall is matterB's 2026-03-20 deposit. matterA's freshest is
    // 2026-03-09. Limit=1 must return the matterB row.
    mockMvc
        .perform(
            get("/portal/trust/movements")
                .param("limit", "1")
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].occurredAt").value("2026-03-20T12:00:00Z"))
        .andExpect(jsonPath("$[0].matterName").value("Matter B"))
        .andExpect(jsonPath("$[0].type").value("DEPOSIT"))
        .andExpect(jsonPath("$[0].currency").value("ZAR"))
        .andExpect(jsonPath("$[0].amount").value(250.00))
        .andExpect(jsonPath("$[0].description").value("Matter B seed"));
  }

  @Test
  void movements_excludes_transactions_from_other_customers() throws Exception {
    when(moduleGuard.isModuleEnabled(TRUST_MODULE)).thenReturn(true);

    // Seed an extra deposit on the unrelated customer with a future-dated occurredAt so it would
    // dominate the ordering if cross-customer leakage occurred.
    portalTrustRepo.upsertTransaction(
        UUID.randomUUID(),
        otherCustomerId,
        otherCustomerMatter,
        "DEPOSIT",
        new BigDecimal("9999.00"),
        new BigDecimal("9999.00"),
        Instant.parse("2030-01-01T00:00:00Z"),
        "Other-customer leak probe",
        "REF-LEAK");

    // limit=50 covers every legitimate row (16) without saturating. Leaked row must be absent.
    mockMvc
        .perform(
            get("/portal/trust/movements")
                .param("limit", "50")
                .header("Authorization", "Bearer " + portalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(16))
        .andExpect(jsonPath("$[?(@.description == 'Other-customer leak probe')]").doesNotExist());
  }

  @Test
  void movements_returns_404_when_trust_module_is_disabled() throws Exception {
    when(moduleGuard.isModuleEnabled(anyString())).thenReturn(false);
    try {
      mockMvc
          .perform(get("/portal/trust/movements").header("Authorization", "Bearer " + portalToken))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.title").value("Trust ledger not available"));
    } finally {
      when(moduleGuard.isModuleEnabled(TRUST_MODULE)).thenReturn(true);
    }
  }
}

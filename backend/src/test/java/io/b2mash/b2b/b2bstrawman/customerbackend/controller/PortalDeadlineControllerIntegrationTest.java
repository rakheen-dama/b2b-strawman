package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalDeadlineView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalDeadlineViewRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.time.Instant;
import java.time.LocalDate;
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
 * Integration tests for {@link PortalDeadlineController} (Epic 497A). Covers the 4 scenarios
 * specified in task 497.7:
 *
 * <ol>
 *   <li>Default range (no {@code from}/{@code to}) returns the next-60-day deadlines.
 *   <li>{@code status=OVERDUE} filter narrows the response to overdue rows only.
 *   <li>Tenant without the {@code deadlines} module → 404 with a deadline-specific ProblemDetail
 *       (ADR-254: portal hides disabled modules).
 *   <li>Detail endpoint resolves a row by {@code (sourceEntity, id)}.
 * </ol>
 *
 * <p>The module gate is asserted via a {@link VerticalModuleGuard} spy — the same pattern used by
 * {@code PortalRetainerControllerIntegrationTest} — so we can flip the module on/off per test
 * without paying for a second tenant provision.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalDeadlineControllerIntegrationTest {

  private static final String DEADLINES_MODULE = "deadlines";

  private static final String ORG_LEGAL = "org_portal_deadline_ctrl_legal";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PortalDeadlineViewRepository deadlineRepo;

  @MockitoBean private VerticalModuleGuard moduleGuard;

  private UUID legalCustomerId;
  private String legalPortalToken;

  // Seeded deadline ids — stable across tests so the detail endpoint scenario can look up a
  // known-good row. All rows belong to legalCustomerId so default-range covers them.
  private UUID upcomingId;
  private UUID dueSoonId;
  private UUID overdueId;
  // A far-future row (90 days out) to prove the default 60-day window excludes it.
  private UUID farFutureId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_LEGAL, "Portal Deadline Ctrl — Legal", "legal-za");
    UUID legalMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_LEGAL,
                "user_deadline_ctrl_legal_owner",
                "deadline-ctrl-legal-owner@test.com",
                "Lee Owner",
                "owner"));
    String legalSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_LEGAL).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, legalSchema)
        .where(RequestScopes.ORG_ID, ORG_LEGAL)
        .where(RequestScopes.MEMBER_ID, legalMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var customer =
                  customerRepository.save(
                      TestCustomerFactory.createActiveCustomer(
                          "Legal Deadline Customer", "legal-deadline@test.com", legalMemberId));
              legalCustomerId = customer.getId();
              portalContactService.createContact(
                  ORG_LEGAL,
                  legalCustomerId,
                  "legal-deadline-contact@test.com",
                  "Legal Portal Contact",
                  PortalContact.ContactRole.PRIMARY);
            });

    legalPortalToken = portalJwtService.issueToken(legalCustomerId, ORG_LEGAL);

    // Seed 4 deadline rows directly via the repo — controller tests must not drive the event
    // path (that's the sync-service test's job). Use relative dates anchored on today so the
    // default 60-day window matches regardless of when CI runs.
    LocalDate today = LocalDate.now();
    upcomingId = UUID.randomUUID();
    deadlineRepo.upsert(
        new PortalDeadlineView(
            upcomingId,
            "FILING_SCHEDULE",
            legalCustomerId,
            null,
            "FILING",
            "VAT 201 filing",
            today.plusDays(30),
            "UPCOMING",
            "Quarterly VAT return",
            Instant.now()));
    dueSoonId = UUID.randomUUID();
    deadlineRepo.upsert(
        new PortalDeadlineView(
            dueSoonId,
            "COURT_DATE",
            legalCustomerId,
            null,
            "COURT_DATE",
            "Motion hearing",
            today.plusDays(5),
            "DUE_SOON",
            "First motion hearing",
            Instant.now()));
    overdueId = UUID.randomUUID();
    deadlineRepo.upsert(
        new PortalDeadlineView(
            overdueId,
            "PRESCRIPTION_TRACKER",
            legalCustomerId,
            null,
            "PRESCRIPTION",
            "Prescription deadline",
            today.minusDays(3),
            "OVERDUE",
            "Claim prescribes",
            Instant.now()));
    farFutureId = UUID.randomUUID();
    deadlineRepo.upsert(
        new PortalDeadlineView(
            farFutureId,
            "CUSTOM_FIELD_DATE",
            legalCustomerId,
            null,
            "CUSTOM_DATE",
            "Annual review",
            today.plusDays(90),
            "UPCOMING",
            "Annual retainer review",
            Instant.now()));

    // Default: deadlines module enabled. Individual tests flip off/on as needed.
    when(moduleGuard.isModuleEnabled(DEADLINES_MODULE)).thenReturn(true);
    when(moduleGuard.getEnabledModules()).thenReturn(Set.of(DEADLINES_MODULE));
  }

  // ==========================================================================
  // Scenario 1 — default range returns the next-60-day deadlines
  // ==========================================================================

  @Test
  void list_defaultRangeReturnsNextSixtyDayDeadlines() throws Exception {
    when(moduleGuard.isModuleEnabled(DEADLINES_MODULE)).thenReturn(true);

    // today..today+60d covers upcoming (today+30), dueSoon (today+5). It EXCLUDES overdue
    // (today-3, before today) and farFuture (today+90, past the 60-day window). Ordered by
    // due_date ASC, so dueSoon is first.
    mockMvc
        .perform(get("/portal/deadlines").header("Authorization", "Bearer " + legalPortalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(dueSoonId.toString()))
        .andExpect(jsonPath("$[0].status").value("DUE_SOON"))
        .andExpect(jsonPath("$[1].id").value(upcomingId.toString()))
        .andExpect(jsonPath("$[1].status").value("UPCOMING"))
        .andExpect(jsonPath("$[?(@.id == '" + overdueId + "')]").isEmpty())
        .andExpect(jsonPath("$[?(@.id == '" + farFutureId + "')]").isEmpty());
  }

  // ==========================================================================
  // Scenario 2 — status=OVERDUE filter narrows to overdue only
  // ==========================================================================

  @Test
  void list_statusOverdueFilterNarrowsToOverdueOnly() throws Exception {
    when(moduleGuard.isModuleEnabled(DEADLINES_MODULE)).thenReturn(true);

    // Widen the date range to include overdue (today-3). Without an explicit `from` that dips
    // into the past the default today..today+60d window would exclude it — so supply from.
    mockMvc
        .perform(
            get("/portal/deadlines")
                .param("from", LocalDate.now().minusDays(30).toString())
                .param("status", "OVERDUE")
                .header("Authorization", "Bearer " + legalPortalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(overdueId.toString()))
        .andExpect(jsonPath("$[0].status").value("OVERDUE"))
        .andExpect(jsonPath("$[0].sourceEntity").value("PRESCRIPTION_TRACKER"));
  }

  // ==========================================================================
  // Scenario 3 — module disabled tenant → 404 + deadline-specific ProblemDetail
  // ==========================================================================

  @Test
  void list_returns404WhenDeadlinesModuleIsDisabled() throws Exception {
    when(moduleGuard.isModuleEnabled(anyString())).thenReturn(false);

    try {
      mockMvc
          .perform(get("/portal/deadlines").header("Authorization", "Bearer " + legalPortalToken))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.title").value("Deadlines not available"))
          .andExpect(
              jsonPath("$.detail")
                  .value("Deadline visibility is not available for this organization"));
    } finally {
      // Restore default for subsequent PER_CLASS tests.
      when(moduleGuard.isModuleEnabled(DEADLINES_MODULE)).thenReturn(true);
    }
  }

  // ==========================================================================
  // Scenario 4 — detail endpoint resolves (sourceEntity, id)
  // ==========================================================================

  @Test
  void get_detailEndpointResolvesBySourceEntityAndId() throws Exception {
    when(moduleGuard.isModuleEnabled(DEADLINES_MODULE)).thenReturn(true);

    mockMvc
        .perform(
            get("/portal/deadlines/{sourceEntity}/{id}", "FILING_SCHEDULE", upcomingId)
                .header("Authorization", "Bearer " + legalPortalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(upcomingId.toString()))
        .andExpect(jsonPath("$.sourceEntity").value("FILING_SCHEDULE"))
        .andExpect(jsonPath("$.deadlineType").value("FILING"))
        .andExpect(jsonPath("$.label").value("VAT 201 filing"))
        .andExpect(jsonPath("$.status").value("UPCOMING"));
  }
}

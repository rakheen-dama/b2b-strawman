package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRetainerConsumptionEntryView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRetainerSummaryView;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalRetainerConsumptionEntryRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalRetainerSummaryRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContact;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactService;
import io.b2mash.b2b.b2bstrawman.portal.PortalJwtService;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.math.BigDecimal;
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
 * Integration tests for {@link PortalRetainerController} (Epic 496A). Covers 5 scenarios:
 *
 * <ol>
 *   <li>consulting-za portal contact lists their retainers.
 *   <li>Module disabled → 404 with retainer-specific ProblemDetail.
 *   <li>legal-za tenant with retainer-backed matter returns the retainer identically.
 *   <li>{@code from}/{@code to} date range filters consumption entries.
 *   <li>Cross-tenant isolation: consulting-za token cannot read legal-za retainer (ADR-253).
 * </ol>
 *
 * <p>The module gate is asserted via a {@link VerticalModuleGuard} spy (same pattern as {@link
 * PortalTrustControllerIntegrationTest}) — provisioning a second tenant with the module disabled
 * would double test runtime with no extra coverage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalRetainerControllerIntegrationTest {

  private static final String RETAINER_MODULE = "retainer_agreements";

  private static final String ORG_CONSULTING = "org_portal_retainer_ctrl_consulting";
  private static final String ORG_LEGAL = "org_portal_retainer_ctrl_legal";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private PortalContactService portalContactService;
  @Autowired private PortalJwtService portalJwtService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private PortalRetainerSummaryRepository summaryRepo;
  @Autowired private PortalRetainerConsumptionEntryRepository entryRepo;

  @MockitoBean private VerticalModuleGuard moduleGuard;

  // consulting-za setup
  private UUID consultingCustomerId;
  private UUID consultingRetainerId;
  private String consultingPortalToken;

  // legal-za setup
  private UUID legalCustomerId;
  private UUID legalRetainerId;
  private String legalPortalToken;

  @BeforeAll
  void setup() throws Exception {
    // --- Consulting tenant ---
    provisioningService.provisionTenant(
        ORG_CONSULTING, "Portal Retainer Ctrl — Consulting", "consulting-za");
    UUID consultingMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_CONSULTING,
                "user_retainer_ctrl_consulting_owner",
                "retainer-ctrl-consulting-owner@test.com",
                "Cathy Owner",
                "owner"));
    String consultingSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_CONSULTING).orElseThrow().getSchemaName();

    ScopedValue.where(RequestScopes.TENANT_ID, consultingSchema)
        .where(RequestScopes.ORG_ID, ORG_CONSULTING)
        .where(RequestScopes.MEMBER_ID, consultingMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(
            () -> {
              var customer =
                  customerRepository.save(
                      TestCustomerFactory.createActiveCustomer(
                          "Consulting Retainer Customer",
                          "consulting-retainer@test.com",
                          consultingMemberId));
              consultingCustomerId = customer.getId();
              portalContactService.createContact(
                  ORG_CONSULTING,
                  consultingCustomerId,
                  "consulting-retainer-contact@test.com",
                  "Consulting Portal Contact",
                  PortalContact.ContactRole.PRIMARY);
            });

    consultingPortalToken = portalJwtService.issueToken(consultingCustomerId, ORG_CONSULTING);
    consultingRetainerId = UUID.randomUUID();
    seedRetainer(
        consultingRetainerId,
        consultingCustomerId,
        "Consulting Monthly Hours",
        LocalDate.of(2026, 3, 1),
        LocalDate.of(2026, 4, 1));
    seedConsumptionEntries(consultingRetainerId, consultingCustomerId);

    // --- Legal tenant ---
    provisioningService.provisionTenant(ORG_LEGAL, "Portal Retainer Ctrl — Legal", "legal-za");
    UUID legalMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_LEGAL,
                "user_retainer_ctrl_legal_owner",
                "retainer-ctrl-legal-owner@test.com",
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
                          "Legal Retainer Customer", "legal-retainer@test.com", legalMemberId));
              legalCustomerId = customer.getId();
              portalContactService.createContact(
                  ORG_LEGAL,
                  legalCustomerId,
                  "legal-retainer-contact@test.com",
                  "Legal Portal Contact",
                  PortalContact.ContactRole.PRIMARY);
            });

    legalPortalToken = portalJwtService.issueToken(legalCustomerId, ORG_LEGAL);
    legalRetainerId = UUID.randomUUID();
    seedRetainer(
        legalRetainerId,
        legalCustomerId,
        "Legal Monthly Hours",
        LocalDate.of(2026, 3, 1),
        LocalDate.of(2026, 4, 1));

    // Default: module enabled. Individual tests flip as needed.
    when(moduleGuard.isModuleEnabled(RETAINER_MODULE)).thenReturn(true);
    when(moduleGuard.getEnabledModules()).thenReturn(Set.of(RETAINER_MODULE));
  }

  // ==========================================================================
  // Scenario 1 — consulting-za portal contact lists their retainer
  // ==========================================================================

  @Test
  void list_returnsRetainersOwnedByTheAuthenticatedCustomerOnly() throws Exception {
    when(moduleGuard.isModuleEnabled(RETAINER_MODULE)).thenReturn(true);

    mockMvc
        .perform(
            get("/portal/retainers").header("Authorization", "Bearer " + consultingPortalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(consultingRetainerId.toString()))
        .andExpect(jsonPath("$[0].name").value("Consulting Monthly Hours"))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$[0].hoursAllotted").value(40))
        .andExpect(jsonPath("$[0].hoursConsumed").value(5.00))
        .andExpect(jsonPath("$[0].hoursRemaining").value(35.00));
  }

  // ==========================================================================
  // Scenario 2 — module disabled → 404 + retainer-specific ProblemDetail
  // ==========================================================================

  @Test
  void list_returns404WhenRetainerModuleIsDisabled() throws Exception {
    when(moduleGuard.isModuleEnabled(anyString())).thenReturn(false);

    try {
      mockMvc
          .perform(
              get("/portal/retainers").header("Authorization", "Bearer " + consultingPortalToken))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.title").value("Retainers not available"))
          .andExpect(
              jsonPath("$.detail").value("Retainer usage is not available for this organization"));
    } finally {
      // Restore default so later tests (within PER_CLASS) see enabled.
      when(moduleGuard.isModuleEnabled(RETAINER_MODULE)).thenReturn(true);
    }
  }

  // ==========================================================================
  // Scenario 3 — legal-za tenant surfaces its retainer through the same endpoint
  // ==========================================================================

  @Test
  void list_returnsRetainerForLegalTenantPortalContact() throws Exception {
    when(moduleGuard.isModuleEnabled(RETAINER_MODULE)).thenReturn(true);

    mockMvc
        .perform(get("/portal/retainers").header("Authorization", "Bearer " + legalPortalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(legalRetainerId.toString()))
        .andExpect(jsonPath("$[0].name").value("Legal Monthly Hours"));
  }

  // ==========================================================================
  // Scenario 4 — from/to date range filters consumption entries
  // ==========================================================================

  @Test
  void consumption_fromToFiltersOutOfRangeEntries() throws Exception {
    when(moduleGuard.isModuleEnabled(RETAINER_MODULE)).thenReturn(true);

    // Full range — all 3 seeded entries visible.
    mockMvc
        .perform(
            get("/portal/retainers/{id}/consumption", consultingRetainerId)
                .header("Authorization", "Bearer " + consultingPortalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));

    // Narrow to 2026-03-10..2026-03-20 — only the middle entry (2026-03-15) is in range.
    mockMvc
        .perform(
            get("/portal/retainers/{id}/consumption", consultingRetainerId)
                .param("from", "2026-03-10")
                .param("to", "2026-03-20")
                .header("Authorization", "Bearer " + consultingPortalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].description").value("Mid-March consumption"));
  }

  // ==========================================================================
  // Scenario 5 — cross-tenant isolation (ADR-253): a portal contact for
  // tenant A cannot read tenant B's retainer or its consumption history,
  // and the list endpoint never leaks tenant B's rows. Most security-critical
  // assertion for this epic — the portal schema is shared across tenants and
  // is keyed on customer_id; a missing scope check would let any contact pull
  // any other tenant's retainer by guessing a UUID.
  // ==========================================================================

  @Test
  void consulting_cannotReadLegalRetainerOrItsConsumption() throws Exception {
    when(moduleGuard.isModuleEnabled(RETAINER_MODULE)).thenReturn(true);

    // Direct hit on the legal retainer's id with the consulting token must 404, NOT 200.
    mockMvc
        .perform(
            get("/portal/retainers/{id}/consumption", legalRetainerId)
                .header("Authorization", "Bearer " + consultingPortalToken))
        .andExpect(status().isNotFound());

    // The list endpoint must return only the consulting retainer — never the legal one — even
    // though both rows live in the same shared portal schema.
    mockMvc
        .perform(
            get("/portal/retainers").header("Authorization", "Bearer " + consultingPortalToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(consultingRetainerId.toString()))
        .andExpect(jsonPath("$[?(@.id == '" + legalRetainerId + "')]").isEmpty());
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  private void seedRetainer(
      UUID retainerId, UUID customerId, String name, LocalDate periodStart, LocalDate periodEnd) {
    summaryRepo.upsert(
        new PortalRetainerSummaryView(
            retainerId,
            customerId,
            name,
            "MONTHLY",
            new BigDecimal("40.00"),
            new BigDecimal("5.00"),
            new BigDecimal("35.00"),
            periodStart,
            periodEnd,
            BigDecimal.ZERO,
            periodEnd,
            "ACTIVE",
            null));
  }

  private void seedConsumptionEntries(UUID retainerId, UUID customerId) {
    entryRepo.upsert(
        new PortalRetainerConsumptionEntryView(
            UUID.randomUUID(),
            retainerId,
            customerId,
            LocalDate.of(2026, 3, 5),
            new BigDecimal("2.00"),
            "Early-March consumption",
            "Cathy (Owner)",
            null));
    entryRepo.upsert(
        new PortalRetainerConsumptionEntryView(
            UUID.randomUUID(),
            retainerId,
            customerId,
            LocalDate.of(2026, 3, 15),
            new BigDecimal("2.00"),
            "Mid-March consumption",
            "Cathy (Owner)",
            null));
    entryRepo.upsert(
        new PortalRetainerConsumptionEntryView(
            UUID.randomUUID(),
            retainerId,
            customerId,
            LocalDate.of(2026, 3, 25),
            new BigDecimal("1.00"),
            "Late-March consumption",
            "Cathy (Owner)",
            null));
  }
}

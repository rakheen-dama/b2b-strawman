package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.time.LocalDate;
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

/**
 * §14.12 #2 / founder ruling §9.2 (ADR-362): a mistaken DRAFT interest run can be deleted together
 * with its allocations; APPROVED runs cannot; the endpoint requires MANAGE_TRUST.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterestRunDeleteIntegrationTest {

  private static final String ORG_ID = "org_interest_delete_test";
  private static final String OWNER_SUBJECT = "user_int_del_owner";
  private static final String ADMIN_SUBJECT = "user_int_del_admin";
  private static final String MEMBER_SUBJECT = "user_int_del_member";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private InterestService interestService;
  @Autowired private InterestRunRepository interestRunRepository;
  @Autowired private InterestAllocationRepository interestAllocationRepository;
  @Autowired private AuditEventRepository auditEventRepository;

  private String tenantSchema;
  private UUID trustAccountId;
  private UUID ownerMemberId;
  private UUID adminMemberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Interest Delete Test Org", null).schemaName();

    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                OWNER_SUBJECT,
                "int_del_owner@test.com",
                "Int Del Owner",
                "owner"));
    adminMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                ADMIN_SUBJECT,
                "int_del_admin@test.com",
                "Int Del Admin",
                "admin"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, MEMBER_SUBJECT, "int_del_member@test.com", "Int Del Member", "member");

    // Enable the trust_accounting module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT);

    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Interest Delete Test Trust Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000630",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = UUID.fromString(TestEntityHelper.extractId(accountResult));

    var customerId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Interest Delete Client", "int_del_client@test.com");

    // Deposited before every period used below, so each calculated run has an allocation
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "amount": 50000.00,
                      "reference": "DEP-DEL-001",
                      "description": "Test deposit for interest-run delete tests",
                      "transactionDate": "2026-01-15"
                    }
                    """
                        .formatted(customerId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/lpff-rates")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "effectiveFrom": "2026-01-01",
                      "ratePercent": 0.0750,
                      "lpffSharePercent": 0.7500,
                      "notes": "Delete test rate"
                    }
                    """))
        .andExpect(status().isCreated());
  }

  private UUID createRun(LocalDate start, LocalDate end) throws Exception {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .call(() -> interestService.createInterestRun(trustAccountId, start, end))
        .id();
  }

  private UUID createAndCalculate(LocalDate start, LocalDate end) throws Exception {
    var runId = createRun(start, end);
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> interestService.calculateInterest(runId));
    return runId;
  }

  private int allocationsOf(UUID runId) throws Exception {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> interestAllocationRepository.findByInterestRunId(runId).size());
  }

  private boolean runExists(UUID runId) throws Exception {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> interestRunRepository.findById(runId).isPresent());
  }

  @Test
  void deleteCalculatedDraft_removesRunAndAllocations_andAudits() throws Exception {
    var runId = createAndCalculate(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
    int before = allocationsOf(runId);
    assertThat(before).isPositive();

    mockMvc
        .perform(
            delete("/api/interest-runs/" + runId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT)))
        .andExpect(status().isNoContent());

    assertThat(runExists(runId)).isFalse();
    assertThat(allocationsOf(runId)).isZero();

    var page =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    auditEventRepository.findByFilter(
                        "interest_run",
                        runId,
                        null,
                        "interest_run.deleted",
                        null,
                        null,
                        Pageable.ofSize(10)));
    assertThat(page.getContent()).hasSize(1);
    var details = page.getContent().getFirst().getDetails();
    assertThat(details)
        .containsEntry("period_start", "2026-03-01")
        .containsEntry("period_end", "2026-03-31")
        .containsEntry("created_by", ownerMemberId.toString())
        .containsKeys("total_interest", "total_lpff_share", "total_client_share");
    assertThat(((Number) details.get("allocation_count")).intValue()).isEqualTo(before);
  }

  @Test
  void deleteNeverCalculatedDraft_freesItsPeriod() throws Exception {
    var start = LocalDate.of(2026, 6, 1);
    var end = LocalDate.of(2026, 6, 30);
    var runId = createRun(start, end);

    mockMvc
        .perform(
            delete("/api/interest-runs/" + runId)
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN_SUBJECT)))
        .andExpect(status().isNoContent());

    assertThat(runExists(runId)).isFalse();
    // The overlap guard no longer sees the deleted draft: the same period can be run again
    var replacementId = createRun(start, end);
    assertThat(replacementId).isNotEqualTo(runId);
    assertThat(runExists(replacementId)).isTrue();
  }

  @Test
  void deleteApprovedRun_isRefusedWithProblemDetail_andNothingIsDeleted() throws Exception {
    var runId = createAndCalculate(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
    // A second member approves (the creator cannot)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.MEMBER_ID, adminMemberId)
        .call(() -> interestService.approveInterestRun(runId));
    int before = allocationsOf(runId);
    assertThat(before).isPositive();

    mockMvc
        .perform(
            delete("/api/interest-runs/" + runId)
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid interest run state"))
        .andExpect(jsonPath("$.detail").value("Only DRAFT interest runs can be deleted"));

    var status =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestRunRepository.findById(runId).orElseThrow().getStatus());
    assertThat(status).isEqualTo("APPROVED");
    assertThat(allocationsOf(runId)).isEqualTo(before);
  }

  @Test
  void deleteWithoutManageTrust_isForbidden_andNothingIsDeleted() throws Exception {
    var runId = createAndCalculate(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
    int before = allocationsOf(runId);
    assertThat(before).isPositive();

    // The member role has VIEW_TRUST but not MANAGE_TRUST
    mockMvc
        .perform(
            delete("/api/interest-runs/" + runId)
                .with(TestJwtFactory.memberJwt(ORG_ID, MEMBER_SUBJECT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access denied"));

    assertThat(runExists(runId)).isTrue();
    assertThat(allocationsOf(runId)).isEqualTo(before);
  }

  @Test
  void deleteUnknownRun_returns404() throws Exception {
    mockMvc
        .perform(
            delete("/api/interest-runs/" + UUID.randomUUID())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT)))
        .andExpect(status().isNotFound());
  }
}

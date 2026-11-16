package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.ProblemDetailAssertions;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 645A (§16.3.1, ADR-375): interest on trust money vests in the Legal Practitioners'
 * Fidelity Fund (section 86(5) of the Legal Practice Act), so a calculated interest run is never
 * approved or posted. Both endpoints answer 400 with the same ProblemDetail before any other check,
 * and nothing reaches trust_transactions. Creating, calculating, viewing and deleting a draft keep
 * working until 651A removes the run (and this test with it).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterestPostingGuardIntegrationTest {

  static final String TITLE = "Interest runs are not posted";
  static final String DETAIL =
      "Interest on trust money vests in the Legal Practitioners' Fidelity Fund (section 86(5) of the"
          + " Legal Practice Act). Kazi no longer posts calculated interest. Record interest from the"
          + " bank statement instead.";

  private static final String ORG_ID = "org_interest_posting_guard";
  private static final String OWNER_SUBJECT = "user_int_pg_owner";
  private static final String ADMIN_SUBJECT = "user_int_pg_admin";

  @Autowired private MockMvc mockMvc;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private InterestService interestService;
  @Autowired private InterestRunRepository interestRunRepository;
  @Autowired private TrustTransactionRepository trustTransactionRepository;

  private String tenantSchema;
  private UUID trustAccountId;
  private UUID ownerMemberId;
  private UUID adminMemberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Interest Posting Guard Org", null)
            .schemaName();
    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUBJECT, "int_pg_owner@test.com", "IPG Owner", "owner"));
    adminMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, ADMIN_SUBJECT, "int_pg_admin@test.com", "IPG Admin", "admin"));

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
                          "accountName": "Interest Posting Guard Trust Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000009245",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2025-01-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = UUID.fromString(TestEntityHelper.extractId(accountResult));

    var customerId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Interest Posting Guard Client", "int_pg_client@test.com");
    UUID matterId =
        TestTrustMatterHelper.openClientMatter(
            tenantSchema,
            transactionTemplate,
            projectRepository,
            UUID.fromString(customerId),
            "Posting guard trust matter",
            ownerMemberId);

    // Deposited before every period below, so each calculated run has an allocation to post
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": 50000.00,
                      "reference": "DEP-IPG-001",
                      "description": "Posting guard deposit",
                      "transactionDate": "2025-01-15"
                    }
                    """
                        .formatted(customerId, matterId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/lpff-rates")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "effectiveFrom": "2025-01-01",
                      "ratePercent": 0.0750,
                      "lpffSharePercent": 0.7500,
                      "notes": "Posting guard rate"
                    }
                    """))
        .andExpect(status().isCreated());
  }

  @Test
  void postOnApprovedRun_isRefusedWithTheSection86_5Message() throws Exception {
    var runId = createAndCalculate(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31));
    markApproved(runId);
    long before = rowsOnAccount();

    ProblemDetailAssertions.assertProblemWithDetail(
        mockMvc.perform(
            post("/api/interest-runs/" + runId + "/post")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT))),
        HttpStatus.BAD_REQUEST,
        TITLE,
        DETAIL);

    var run = runOf(runId);
    assertThat(run.getStatus()).isEqualTo("APPROVED");
    assertThat(run.getPostedAt()).isNull();
    assertThat(rowsOnAccount() - before).isZero();
  }

  @Test
  void approveOnDraftRun_isRefusedWithTheSection86_5Message() throws Exception {
    var runId = createAndCalculate(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30));

    // A second member (not the creator): the refusal does not come from the two-member rule
    ProblemDetailAssertions.assertProblemWithDetail(
        mockMvc.perform(
            post("/api/interest-runs/" + runId + "/approve")
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN_SUBJECT))),
        HttpStatus.BAD_REQUEST,
        TITLE,
        DETAIL);

    var run = runOf(runId);
    assertThat(run.getStatus()).isEqualTo("DRAFT");
    assertThat(run.getApprovedBy()).isNull();
  }

  @Test
  void refusedApproveAndPost_writeNoTrustTransaction() throws Exception {
    var runId = createAndCalculate(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31));
    long before = rowsOnAccount();

    ProblemDetailAssertions.assertProblemWithDetail(
        mockMvc.perform(
            post("/api/interest-runs/" + runId + "/approve")
                .with(TestJwtFactory.adminJwt(ORG_ID, ADMIN_SUBJECT))),
        HttpStatus.BAD_REQUEST,
        TITLE,
        DETAIL);
    markApproved(runId);
    ProblemDetailAssertions.assertProblemWithDetail(
        mockMvc.perform(
            post("/api/interest-runs/" + runId + "/post")
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUBJECT))),
        HttpStatus.BAD_REQUEST,
        TITLE,
        DETAIL);

    // Id-scoped: this run's account only, and no row carries this run's reference
    assertThat(rowsOnAccount() - before).isZero();
    assertThat(rowsReferencing(runId)).isZero();
  }

  // --- Helpers ---

  private UUID createAndCalculate(LocalDate start, LocalDate end) {
    var runId =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(() -> interestService.createInterestRun(trustAccountId, start, end))
            .id();
    var calculated =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> interestService.calculateInterest(runId));
    assertThat(calculated.allocationCount()).as("the run has an allocation to post").isPositive();
    return runId;
  }

  /** A run approved before the 645A guard (InterestPeriodGuardIntegrationTest.markApproved). */
  private void markApproved(UUID runId) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var run = interestRunRepository.findById(runId).orElseThrow();
                      run.setStatus("APPROVED");
                      run.setApprovedBy(adminMemberId);
                      interestRunRepository.save(run);
                    }));
  }

  private InterestRun runOf(UUID runId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> interestRunRepository.findById(runId).orElseThrow());
  }

  private long rowsOnAccount() {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(
            () ->
                trustTransactionRepository
                    .findByTrustAccountIdOrderByTransactionDateDesc(
                        trustAccountId, Pageable.unpaged())
                    .getTotalElements());
  }

  private long rowsReferencing(UUID runId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(
            () ->
                trustTransactionRepository
                    .findByTrustAccountIdOrderByTransactionDateDesc(
                        trustAccountId, Pageable.unpaged())
                    .getContent()
                    .stream()
                    .filter(t -> t.getReference() != null)
                    .filter(t -> t.getReference().contains(runId.toString()))
                    .count());
  }
}

package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89-F11: an interest run may only cover a period that has ended (or ends today, South African
 * time). Without the guard a run for a period ending tomorrow was created, approved and posted, and
 * its INTEREST_CREDIT rows were dated in the future.
 *
 * <p>Dates are relative to the real SA clock ({@link KaziTime#ZONE}, the zone of the {@code Clock}
 * bean), so no {@code @MockitoBean Clock} and no extra Spring context cache key. Each test uses its
 * own funded trust account so one test's run can never overlap another's.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InterestPeriodGuardIntegrationTest {

  private static final String ORG_ID = "org_interest_period_guard";
  private static final String OWNER = "user_int_guard_owner";
  private static final String NOT_ENDED_TITLE = "Interest period has not ended";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private InterestRunRepository interestRunRepository;
  @Autowired private TrustTransactionRepository trustTransactionRepository;

  private String tenantSchema;
  private String customerId;

  private record FundedAccount(UUID accountId, UUID lpffRateId) {}

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Interest Period Guard Test Org", null)
            .schemaName();

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, OWNER, "int_guard_owner@test.com", "Int Guard Owner", "owner");

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    customerId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt(), "Interest Guard Client", "int_guard_client@test.com");
  }

  // --- create ---

  @Test
  void createInterestRun_periodEndAfterToday_rejected400() throws Exception {
    var account = createFundedAccount("62000000901");
    var today = LocalDate.now(KaziTime.ZONE);
    var tomorrow = today.plusDays(1);

    mockMvc
        .perform(createRunRequest(account.accountId(), today, tomorrow))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value(NOT_ENDED_TITLE))
        .andExpect(jsonPath("$.detail").value(Matchers.containsString(tomorrow.toString())))
        .andExpect(jsonPath("$.detail").value(Matchers.containsString(today.toString())));

    assertThat(runsFor(account.accountId())).isEmpty();
  }

  @Test
  void createInterestRun_periodEndToday_allowed() throws Exception {
    var account = createFundedAccount("62000000902");
    var today = LocalDate.now(KaziTime.ZONE);

    mockMvc
        .perform(createRunRequest(account.accountId(), today.minusDays(30), today))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.periodEnd").value(today.toString()));
  }

  // --- approve: a DRAFT created before the guard existed ---

  @Test
  void approveInterestRun_legacyDraftWithFuturePeriod_rejected400() throws Exception {
    var account = createFundedAccount("62000000903");
    var today = LocalDate.now(KaziTime.ZONE);
    var runId = saveLegacyRun(account, today, today.plusDays(1));
    calculate(runId);

    mockMvc
        .perform(post("/api/interest-runs/" + runId + "/approve").with(ownerJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value(NOT_ENDED_TITLE));

    assertThat(statusOf(runId)).isEqualTo("DRAFT");
  }

  // --- post: an APPROVED run for a period that has not ended ---

  @Test
  void postInterestRun_futurePeriod_rejected400() throws Exception {
    var account = createFundedAccount("62000000904");
    var today = LocalDate.now(KaziTime.ZONE);
    var runId = saveLegacyRun(account, today, today.plusDays(1));
    calculate(runId);
    markApproved(runId);

    mockMvc
        .perform(post("/api/interest-runs/" + runId + "/post").with(ownerJwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value(NOT_ENDED_TITLE));

    assertThat(statusOf(runId)).isEqualTo("APPROVED");
    // Nothing reached the ledger: no INTEREST_CREDIT / INTEREST_LPFF rows on this account
    var interestTxns =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    trustTransactionRepository
                        .findByTrustAccountIdOrderByTransactionDateDesc(
                            account.accountId(), Pageable.unpaged())
                        .getContent()
                        .stream()
                        .filter(t -> t.getTransactionType().startsWith("INTEREST_"))
                        .toList());
    assertThat(interestTxns).isEmpty();
  }

  // --- Helpers ---

  private MockHttpServletRequestBuilder createRunRequest(
      UUID accountId, LocalDate start, LocalDate end) {
    return post("/api/trust-accounts/" + accountId + "/interest-runs")
        .with(ownerJwt())
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"periodStart": "%s", "periodEnd": "%s"}
            """
                .formatted(start, end));
  }

  /** A trust account with an LPFF rate from 2025-01-01 and a R100,000 deposit for the client. */
  private FundedAccount createFundedAccount(String accountNumber) throws Exception {
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Interest Guard Account %s",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "%s",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2025-01-01"
                        }
                        """
                            .formatted(accountNumber, accountNumber)))
            .andExpect(status().isCreated())
            .andReturn();
    var accountId = UUID.fromString(TestEntityHelper.extractId(accountResult));

    var rateResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + accountId + "/lpff-rates")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "effectiveFrom": "2025-01-01",
                          "ratePercent": 0.0750,
                          "lpffSharePercent": 0.7500,
                          "notes": "Guard test rate"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var rateId = UUID.fromString(TestEntityHelper.extractId(rateResult));

    mockMvc
        .perform(
            post("/api/trust-accounts/" + accountId + "/transactions/deposit")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "amount": 100000.00,
                      "reference": "DEP-GUARD-%s",
                      "description": "Guard test deposit",
                      "transactionDate": "2025-01-15"
                    }
                    """
                        .formatted(customerId, accountNumber)))
        .andExpect(status().isCreated());

    return new FundedAccount(accountId, rateId);
  }

  /** Saves a DRAFT run straight through the repository, bypassing the create guard (pre-fix). */
  private UUID saveLegacyRun(FundedAccount account, LocalDate start, LocalDate end) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(
            () ->
                transactionTemplate.execute(
                    tx ->
                        interestRunRepository
                            .save(
                                new InterestRun(
                                    account.accountId(), start, end, account.lpffRateId()))
                            .getId()));
  }

  private void calculate(UUID runId) throws Exception {
    mockMvc
        .perform(post("/api/interest-runs/" + runId + "/calculate").with(ownerJwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allocationCount").value(1));
  }

  /** Simulates a run approved before the guard existed. */
  private void markApproved(UUID runId) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var run = interestRunRepository.findById(runId).orElseThrow();
                      run.setStatus("APPROVED");
                      interestRunRepository.save(run);
                    }));
  }

  private String statusOf(UUID runId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> interestRunRepository.findById(runId).orElseThrow().getStatus());
  }

  private List<InterestRun> runsFor(UUID accountId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> interestRunRepository.findByTrustAccountIdOrderByPeriodEndDesc(accountId));
  }

  private static JwtRequestPostProcessor ownerJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}

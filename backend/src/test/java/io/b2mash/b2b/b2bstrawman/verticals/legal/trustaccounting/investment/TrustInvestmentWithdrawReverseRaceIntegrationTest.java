package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PR #1757 security review (MEDIUM, race / TOCTOU): an investment withdrawal and a reversal of its
 * placement PAYMENT each check the other's effect (the withdrawal: placement APPROVED; the
 * reversal: investment not WITHDRAWN). Without a common lock both checks pass when the two run
 * together and the client is credited twice. Both paths now lock the placement PAYMENT row first,
 * then the investment row, and re-check under those locks.
 *
 * <p>Each test holds one operation's transaction open (latch) after it has done its work, starts
 * the other operation on a second thread, gives it time to reach its checks, then commits the
 * first. Exactly one may succeed and the client must be credited once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustInvestmentWithdrawReverseRaceIntegrationTest {

  private static final String ORG_ID = "org_trust_investment_withdraw_reverse_race";
  private static final String OWNER_SUB = "user_tiwr_owner";
  private static final String APPROVER_SUB = "user_tiwr_approver";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustInvestmentService investmentService;
  @Autowired private ClientLedgerCardRepository ledgerCardRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private Clock clock;

  private String tenantSchema;
  private UUID ownerId;
  private UUID trustAccountId;
  private LocalDate today;
  private int counter = 0;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Withdraw Reverse Race Org", "legal-za")
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER_SUB, "tiwr_owner@test.com", "TIWR Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, APPROVER_SUB, "tiwr_approver@test.com", "TIWR Approver", "owner");
    today = LocalDate.now(clock);
    trustAccountId =
        as(
            () ->
                transactionTemplate.execute(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                      return trustAccountRepository
                          .saveAndFlush(
                              new TrustAccount(
                                  "Withdraw Reverse Race Trust",
                                  "Test Bank",
                                  "250655",
                                  "6200-000-TIWR",
                                  TrustAccountType.GENERAL,
                                  true,
                                  false,
                                  null,
                                  LocalDate.of(2025, 1, 1),
                                  "PR #1757 race tests"))
                          .getId();
                    }));
  }

  @Test
  void reversalInFlight_thenWithdrawal_onlyTheReversalSucceeds_creditedOnce() throws Exception {
    Placed placed = placeAndApprove("Reverse First");

    var outcome =
        race(
            () -> transactionService.reverseTransaction(placed.placementId(), "Placed in error"),
            () -> investmentService.withdrawInvestment(placed.investmentId()));

    assertThat(outcome.first()).as("the reversal (committed first)").isNull();
    assertThat(outcome.second())
        .as("the withdrawal, checked after the reversal committed")
        .isInstanceOf(InvalidStateException.class);
    assertCreditedOnce(placed);
  }

  @Test
  void withdrawalInFlight_thenReversal_onlyTheWithdrawalSucceeds_creditedOnce() throws Exception {
    Placed placed = placeAndApprove("Withdraw First");

    var outcome =
        race(
            () -> investmentService.withdrawInvestment(placed.investmentId()),
            () -> transactionService.reverseTransaction(placed.placementId(), "Placed in error"));

    assertThat(outcome.first()).as("the withdrawal (committed first)").isNull();
    assertThat(outcome.second())
        .as("the reversal, checked after the withdrawal committed")
        .isInstanceOf(InvalidStateException.class);
    assertCreditedOnce(placed);
  }

  // --- race harness ---

  private record Outcome(Throwable first, Throwable second) {}

  /**
   * Runs {@code first} in a transaction that stays open after it returns, starts {@code second} on
   * another thread, waits for it to reach its checks (it either blocks on a lock or, without one,
   * runs ahead), then commits {@code first}. Returns each side's failure (null = succeeded).
   */
  private Outcome race(Supplier<?> first, Supplier<?> second) throws Exception {
    var firstDone = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    var firstError = new AtomicReference<Throwable>();
    var secondError = new AtomicReference<Throwable>();

    Thread firstThread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    as(
                        () ->
                            transactionTemplate.execute(
                                tx -> {
                                  first.get();
                                  firstDone.countDown();
                                  await(releaseFirst);
                                  return null;
                                }));
                  } catch (Throwable t) {
                    firstError.set(t);
                    firstDone.countDown();
                  }
                });
    assertThat(firstDone.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(firstError.get()).as("first operation").isNull();

    Thread secondThread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    as(second);
                  } catch (Throwable t) {
                    secondError.set(t);
                  }
                });
    // Give the second operation time to read and check (or block on a lock) before committing
    Thread.sleep(1500);
    releaseFirst.countDown();
    firstThread.join(30_000);
    secondThread.join(30_000);
    return new Outcome(firstError.get(), secondError.get());
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** R5 000 deposited, R1 000 placed and approved, then exactly one R1 000 credit back. */
  private void assertCreditedOnce(Placed placed) {
    assertThat(cardBalance(placed.client())).isEqualByComparingTo("5000.00");
    assertThat(matterBalance(placed.matter())).isEqualByComparingTo("5000.00");
  }

  // --- fixtures ---

  private record Placed(UUID client, UUID matter, UUID investmentId, UUID placementId) {}

  private Placed placeAndApprove(String name) throws Exception {
    UUID client = newClient(name + " Client");
    UUID matter = newMatter(client, name + " Matter");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                .with(ownerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"clientId": "%s", "matterId": "%s", "amount": 5000.00,
                     "reference": "TIWR-DEP-%d", "transactionDate": "%s"}
                    """
                        .formatted(client, matter, ++counter, today.minusDays(5))))
        .andExpect(status().isCreated());
    String placed =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/investments")
                    .with(ownerJwt())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"clientId": "%s", "matterId": "%s", "institution": "Test Bank",
                         "accountNumber": "TIWR-%d", "principal": 1000.00, "interestRate": 0.07,
                         "depositDate": "%s", "investmentBasis": "FIRM_DISCRETION"}
                        """
                            .formatted(client, matter, ++counter, today.minusDays(4))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID placementId = UUID.fromString(JsonPath.read(placed, "$.depositTransactionId"));
    mockMvc
        .perform(
            post("/api/trust-transactions/" + placementId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, APPROVER_SUB)))
        .andExpect(status().isOk());
    assertThat(cardBalance(client)).isEqualByComparingTo("4000.00");
    return new Placed(client, matter, UUID.fromString(JsonPath.read(placed, "$.id")), placementId);
  }

  private UUID newClient(String name) {
    int n = ++counter;
    return as(
        () ->
            transactionTemplate.execute(
                tx ->
                    customerRepository
                        .save(
                            TestCustomerFactory.createActiveCustomer(
                                name, "tiwr_client_" + n + "@test.com", ownerId))
                        .getId()));
  }

  private UUID newMatter(UUID client, String name) {
    return as(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var project = new Project(name, "PR #1757", ownerId);
                  project.setCustomerId(client);
                  return projectRepository.saveAndFlush(project).getId();
                }));
  }

  private BigDecimal matterBalance(UUID matter) {
    var balance = as(() -> transactionRepository.calculateBalanceByProjectId(matter));
    return balance == null ? BigDecimal.ZERO : balance;
  }

  private BigDecimal cardBalance(UUID client) {
    return as(
        () ->
            ledgerCardRepository
                .findByTrustAccountIdAndCustomerId(trustAccountId, client)
                .orElseThrow()
                .getBalance());
  }

  private <T> T as(Supplier<T> action) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"))
        .call(action::get);
  }

  private static JwtRequestPostProcessor ownerJwt() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER_SUB);
  }
}

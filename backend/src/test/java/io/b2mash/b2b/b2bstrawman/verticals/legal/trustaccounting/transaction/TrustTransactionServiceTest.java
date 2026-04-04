package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordTransferRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustTransactionServiceTest {

  private static final String API_KEY = "test-api-key";
  private static final String ORG_ID = "org_trust_txn_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private ClientLedgerCardRepository ledgerCardRepository;
  @Autowired private ClientLedgerService clientLedgerService;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Transaction Service Test Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            syncMember(
                ORG_ID, "user_trust_txn_owner", "trust_txn@test.com", "Trust Txn Owner", "owner"));

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

    // Create a trust account for tests
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateTrustAccountRequest(
                          "Test Trust Account",
                          "First National Bank",
                          "250655",
                          "9876543210",
                          "GENERAL",
                          true,
                          false,
                          null,
                          LocalDate.of(2026, 1, 1),
                          "Test account for transactions");

                  var response = trustAccountService.createTrustAccount(request);
                  trustAccountId = response.id();
                }));
  }

  // --- 440.7: Deposit and ledger tests ---

  @Test
  void deposit_createsTransactionWithRecordedStatus() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Deposit Test Client", "deposit@test.com", memberId));

                  var request =
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("10000.00"),
                          "DEP-001",
                          "Initial deposit",
                          LocalDate.of(2026, 3, 1));

                  var response = transactionService.recordDeposit(trustAccountId, request);

                  assertThat(response.id()).isNotNull();
                  assertThat(response.transactionType()).isEqualTo("DEPOSIT");
                  assertThat(response.status()).isEqualTo("RECORDED");
                  assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("10000.00"));
                  assertThat(response.customerId()).isEqualTo(customer.getId());
                  assertThat(response.reference()).isEqualTo("DEP-001");
                  assertThat(response.recordedBy()).isEqualTo(memberId);
                  assertThat(response.createdAt()).isNotNull();
                }));
  }

  @Test
  void deposit_upsertsClientLedgerCardWithCorrectBalance() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Ledger Test Client", "ledger@test.com", memberId));

                  var request =
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("5000.00"),
                          "DEP-002",
                          "Deposit for ledger test",
                          LocalDate.of(2026, 3, 2));

                  transactionService.recordDeposit(trustAccountId, request);

                  var ledger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, customer.getId())
                          .orElseThrow();

                  assertThat(ledger.getBalance()).isEqualByComparingTo(new BigDecimal("5000.00"));
                  assertThat(ledger.getTotalDeposits())
                      .isEqualByComparingTo(new BigDecimal("5000.00"));
                  assertThat(ledger.getLastTransactionDate()).isEqualTo(LocalDate.of(2026, 3, 2));
                }));
  }

  @Test
  void secondDeposit_incrementsExistingLedgerCard() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Multi Deposit Client", "multi@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("3000.00"),
                          "DEP-003A",
                          "First deposit",
                          LocalDate.of(2026, 3, 3)));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("2000.00"),
                          "DEP-003B",
                          "Second deposit",
                          LocalDate.of(2026, 3, 4)));

                  var ledger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, customer.getId())
                          .orElseThrow();

                  assertThat(ledger.getBalance()).isEqualByComparingTo(new BigDecimal("5000.00"));
                  assertThat(ledger.getTotalDeposits())
                      .isEqualByComparingTo(new BigDecimal("5000.00"));
                  assertThat(ledger.getLastTransactionDate()).isEqualTo(LocalDate.of(2026, 3, 4));
                }));
  }

  @Test
  void deposit_emitsAuditEvent() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Audit Deposit Client", "audit_dep@test.com", memberId));

                  long auditCountBefore = auditEventRepository.count();

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("1000.00"),
                          "DEP-004",
                          "Audit test deposit",
                          LocalDate.of(2026, 3, 5)));

                  long auditCountAfter = auditEventRepository.count();
                  assertThat(auditCountAfter).isGreaterThan(auditCountBefore);
                }));
  }

  @Test
  void listClientLedgers_returnsCards() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer1 =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "List Ledger Client 1", "list1@test.com", memberId));

                  var customer2 =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "List Ledger Client 2", "list2@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer1.getId(),
                          null,
                          new BigDecimal("1000.00"),
                          "DEP-005A",
                          null,
                          LocalDate.of(2026, 3, 6)));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer2.getId(),
                          null,
                          new BigDecimal("2000.00"),
                          "DEP-005B",
                          null,
                          LocalDate.of(2026, 3, 6)));

                  var ledgers =
                      clientLedgerService.listClientLedgers(
                          trustAccountId, org.springframework.data.domain.Pageable.unpaged());

                  assertThat(ledgers.getContent().size()).isGreaterThanOrEqualTo(2);
                }));
  }

  @Test
  void getClientTransactionHistory_returnsDeposit() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "History Client", "history@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("7500.00"),
                          "DEP-006",
                          "History test",
                          LocalDate.of(2026, 3, 7)));

                  var history =
                      clientLedgerService.getClientTransactionHistory(
                          customer.getId(),
                          trustAccountId,
                          org.springframework.data.domain.Pageable.unpaged());

                  assertThat(history.getContent()).isNotEmpty();
                  assertThat(history.getContent().get(0).transactionType()).isEqualTo("DEPOSIT");
                  assertThat(history.getContent().get(0).amount())
                      .isEqualByComparingTo(new BigDecimal("7500.00"));
                }));
  }

  @Test
  void transfer_createsPairedTransferOutAndTransferIn() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var sourceCustomer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Transfer Source", "source@test.com", memberId));

                  var targetCustomer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Transfer Target", "target@test.com", memberId));

                  // Fund source account first
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          sourceCustomer.getId(),
                          null,
                          new BigDecimal("10000.00"),
                          "DEP-007",
                          "Funding for transfer",
                          LocalDate.of(2026, 3, 8)));

                  var result =
                      transactionService.recordTransfer(
                          trustAccountId,
                          new RecordTransferRequest(
                              sourceCustomer.getId(),
                              targetCustomer.getId(),
                              null,
                              new BigDecimal("3000.00"),
                              "TRF-001",
                              "Client transfer",
                              LocalDate.of(2026, 3, 9)));

                  assertThat(result).hasSize(2);

                  var transferOut = result.get(0);
                  assertThat(transferOut.transactionType()).isEqualTo("TRANSFER_OUT");
                  assertThat(transferOut.customerId()).isEqualTo(sourceCustomer.getId());
                  assertThat(transferOut.counterpartyCustomerId())
                      .isEqualTo(targetCustomer.getId());
                  assertThat(transferOut.status()).isEqualTo("RECORDED");

                  var transferIn = result.get(1);
                  assertThat(transferIn.transactionType()).isEqualTo("TRANSFER_IN");
                  assertThat(transferIn.customerId()).isEqualTo(targetCustomer.getId());
                  assertThat(transferIn.counterpartyCustomerId()).isEqualTo(sourceCustomer.getId());
                  assertThat(transferIn.status()).isEqualTo("RECORDED");

                  // Both share same reference
                  assertThat(transferOut.reference()).isEqualTo(transferIn.reference());
                }));
  }

  @Test
  void transfer_updatesBothLedgerCards() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var sourceCustomer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Ledger Transfer Source", "ledger_src@test.com", memberId));

                  var targetCustomer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Ledger Transfer Target", "ledger_tgt@test.com", memberId));

                  // Fund source
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          sourceCustomer.getId(),
                          null,
                          new BigDecimal("8000.00"),
                          "DEP-008",
                          null,
                          LocalDate.of(2026, 3, 10)));

                  // Transfer
                  transactionService.recordTransfer(
                      trustAccountId,
                      new RecordTransferRequest(
                          sourceCustomer.getId(),
                          targetCustomer.getId(),
                          null,
                          new BigDecimal("3000.00"),
                          "TRF-002",
                          null,
                          LocalDate.of(2026, 3, 11)));

                  var sourceLedger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, sourceCustomer.getId())
                          .orElseThrow();

                  var targetLedger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, targetCustomer.getId())
                          .orElseThrow();

                  assertThat(sourceLedger.getBalance())
                      .isEqualByComparingTo(new BigDecimal("5000.00"));
                  assertThat(targetLedger.getBalance())
                      .isEqualByComparingTo(new BigDecimal("3000.00"));
                }));
  }

  // --- 440.8: Negative balance prevention tests ---

  @Test
  void transfer_failsWithInsufficientBalance() {
    UUID[] sourceId = new UUID[1];
    UUID[] targetId = new UUID[1];

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var sourceCustomer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Insufficient Source", "insuff_src@test.com", memberId));
                  var targetCustomer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Insufficient Target", "insuff_tgt@test.com", memberId));

                  sourceId[0] = sourceCustomer.getId();
                  targetId[0] = targetCustomer.getId();

                  // Deposit only 1000
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          sourceCustomer.getId(),
                          null,
                          new BigDecimal("1000.00"),
                          "DEP-009",
                          null,
                          LocalDate.of(2026, 3, 12)));
                }));

    // Attempt transfer of 5000 in a new transaction
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionService.recordTransfer(
                            trustAccountId,
                            new RecordTransferRequest(
                                sourceId[0],
                                targetId[0],
                                null,
                                new BigDecimal("5000.00"),
                                "TRF-003",
                                null,
                                LocalDate.of(2026, 3, 13))))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Insufficient trust balance"));
  }

  @Test
  void concurrentTransfers_secondSeesUpdatedBalance() throws Exception {
    UUID[] sourceId = new UUID[1];
    UUID[] target1Id = new UUID[1];
    UUID[] target2Id = new UUID[1];

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var source =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Concurrent Source", "conc_src@test.com", memberId));
                  var target1 =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Concurrent Target 1", "conc_tgt1@test.com", memberId));
                  var target2 =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Concurrent Target 2", "conc_tgt2@test.com", memberId));

                  sourceId[0] = source.getId();
                  target1Id[0] = target1.getId();
                  target2Id[0] = target2.getId();

                  // Deposit 5000
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          source.getId(),
                          null,
                          new BigDecimal("5000.00"),
                          "DEP-010",
                          null,
                          LocalDate.of(2026, 3, 14)));
                }));

    // Two concurrent transfers of 3000 each -- only one should succeed
    var latch = new CountDownLatch(1);
    AtomicReference<Throwable> error1 = new AtomicReference<>();
    AtomicReference<Throwable> error2 = new AtomicReference<>();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      executor.submit(
          () -> {
            try {
              latch.await();
              ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                  .where(RequestScopes.ORG_ID, ORG_ID)
                  .where(RequestScopes.MEMBER_ID, memberId)
                  .where(RequestScopes.ORG_ROLE, "owner")
                  .run(
                      () ->
                          transactionTemplate.executeWithoutResult(
                              tx ->
                                  transactionService.recordTransfer(
                                      trustAccountId,
                                      new RecordTransferRequest(
                                          sourceId[0],
                                          target1Id[0],
                                          null,
                                          new BigDecimal("3000.00"),
                                          "TRF-CONC-A",
                                          null,
                                          LocalDate.of(2026, 3, 15)))));
            } catch (Throwable t) {
              error1.set(t);
            }
          });

      executor.submit(
          () -> {
            try {
              latch.await();
              ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                  .where(RequestScopes.ORG_ID, ORG_ID)
                  .where(RequestScopes.MEMBER_ID, memberId)
                  .where(RequestScopes.ORG_ROLE, "owner")
                  .run(
                      () ->
                          transactionTemplate.executeWithoutResult(
                              tx ->
                                  transactionService.recordTransfer(
                                      trustAccountId,
                                      new RecordTransferRequest(
                                          sourceId[0],
                                          target2Id[0],
                                          null,
                                          new BigDecimal("3000.00"),
                                          "TRF-CONC-B",
                                          null,
                                          LocalDate.of(2026, 3, 15)))));
            } catch (Throwable t) {
              error2.set(t);
            }
          });

      latch.countDown();
      executor.shutdown();
      executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    // Exactly one transfer should fail (insufficient balance) or one should encounter a DB error
    // The key assertion: at most one succeeded, the other was blocked by FOR UPDATE and failed
    boolean firstFailed = error1.get() != null;
    boolean secondFailed = error2.get() != null;
    assertThat(firstFailed || secondFailed)
        .as("At least one concurrent transfer should fail due to insufficient balance")
        .isTrue();

    // Verify final ledger balance is consistent: initial 5000 minus one successful 3000 transfer
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var sourceLedger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, sourceId[0])
                          .orElseThrow();

                  assertThat(sourceLedger.getBalance())
                      .as("Source balance should be 2000 (5000 - one successful 3000 transfer)")
                      .isEqualByComparingTo(new BigDecimal("2000.00"));
                }));
  }

  @Test
  void checkConstraint_preventsNegativeBalance() {
    UUID[] customerId = new UUID[1];

    // Create ledger card via deposit in first transaction
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Check Constraint Client", "check@test.com", memberId));
                  customerId[0] = customer.getId();

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("100.00"),
                          "DEP-011",
                          null,
                          LocalDate.of(2026, 3, 16)));
                }));

    // Attempt to set negative balance directly via SQL in a separate transaction
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE \"%s\".client_ledger_cards SET balance = -1 WHERE customer_id = ?"
                        .formatted(tenantSchema),
                    customerId[0]))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void transfer_withExactBalance_succeeds() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var sourceCustomer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Exact Balance Source", "exact_src@test.com", memberId));

                  var targetCustomer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Exact Balance Target", "exact_tgt@test.com", memberId));

                  // Deposit exactly 2500
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          sourceCustomer.getId(),
                          null,
                          new BigDecimal("2500.00"),
                          "DEP-012",
                          null,
                          LocalDate.of(2026, 3, 17)));

                  // Transfer exactly 2500
                  var result =
                      transactionService.recordTransfer(
                          trustAccountId,
                          new RecordTransferRequest(
                              sourceCustomer.getId(),
                              targetCustomer.getId(),
                              null,
                              new BigDecimal("2500.00"),
                              "TRF-004",
                              null,
                              LocalDate.of(2026, 3, 18)));

                  assertThat(result).hasSize(2);

                  var sourceLedger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, sourceCustomer.getId())
                          .orElseThrow();

                  assertThat(sourceLedger.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
                }));
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

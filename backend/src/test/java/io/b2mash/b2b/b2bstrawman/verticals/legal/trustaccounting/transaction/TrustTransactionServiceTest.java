package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.event.TrustTransactionRecordedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordFeeTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordRefundRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordTransferRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustTransactionServiceTest {
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
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceService invoiceService;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private ApplicationEvents applicationEvents;

  private String tenantSchema;
  private UUID memberId;
  private UUID approverId;
  private UUID secondApproverId;
  private UUID trustAccountId;
  private UUID dualApprovalAccountId;
  private UUID thresholdAccountId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Transaction Service Test Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_trust_txn_owner",
                "trust_txn@test.com",
                "Trust Txn Owner",
                "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_approver", "approver@test.com", "Trust Approver", "owner"));
    secondApproverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_second_approver",
                "second_approver@test.com",
                "Second Approver",
                "owner"));

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

    // Create a trust account with dual approval enabled (no threshold)
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateTrustAccountRequest(
                          "Dual Approval Trust Account",
                          "First National Bank",
                          "250655",
                          "1111111111",
                          "GENERAL",
                          false,
                          true,
                          null,
                          LocalDate.of(2026, 1, 1),
                          "Dual approval test account");

                  var response = trustAccountService.createTrustAccount(request);
                  dualApprovalAccountId = response.id();
                }));

    // Create a trust account with dual approval + threshold of R10,000
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var request =
                      new CreateTrustAccountRequest(
                          "Threshold Trust Account",
                          "First National Bank",
                          "250655",
                          "2222222222",
                          "GENERAL",
                          false,
                          true,
                          new BigDecimal("10000.00"),
                          LocalDate.of(2026, 1, 1),
                          "Threshold test account");

                  var response = trustAccountService.createTrustAccount(request);
                  thresholdAccountId = response.id();
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

  // GAP-L-52: recordDeposit must publish a TrustTransactionRecordedEvent so the portal
  // trust-ledger read-model projects the deposit. The awaiting-approval lifecycle doesn't
  // fire on this direct-RECORDED path, so TrustTransactionApprovalEvent alone is insufficient.
  @Test
  void deposit_publishesTrustTransactionRecordedEvent() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Event Publish Test Client", "deposit-event@test.com", memberId));

                  var response =
                      transactionService.recordDeposit(
                          trustAccountId,
                          new RecordDepositRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("750.00"),
                              "DEP-EVT-1",
                              "Deposit for event publication",
                              LocalDate.of(2026, 3, 1)));

                  var published =
                      applicationEvents.stream(TrustTransactionRecordedEvent.class).toList();
                  assertThat(published)
                      .as("recordDeposit must publish exactly one TrustTransactionRecordedEvent")
                      .anySatisfy(
                          event -> {
                            assertThat(event.eventType()).isEqualTo("trust_transaction.recorded");
                            assertThat(event.transactionId()).isEqualTo(response.id());
                            assertThat(event.trustAccountId()).isEqualTo(trustAccountId);
                            assertThat(event.transactionType()).isEqualTo("DEPOSIT");
                            assertThat(event.amount()).isEqualByComparingTo("750.00");
                            assertThat(event.customerId()).isEqualTo(customer.getId());
                            assertThat(event.recordedBy()).isEqualTo(memberId);
                          });
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

    // Two concurrent transfers of 3000 each -- only one should succeed.
    // CyclicBarrier ensures both threads are ready before either starts.
    var barrier = new CyclicBarrier(2);
    AtomicReference<Throwable> error1 = new AtomicReference<>();
    AtomicReference<Throwable> error2 = new AtomicReference<>();

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      executor.submit(
          () -> {
            try {
              barrier.await();
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
              barrier.await();
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

      executor.shutdown();
      executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
    } finally {
      executor.shutdownNow();
    }

    // Exactly one transfer should succeed and one should fail (XOR)
    boolean firstFailed = error1.get() != null;
    boolean secondFailed = error2.get() != null;
    assertThat(firstFailed ^ secondFailed)
        .as(
            "Exactly one concurrent transfer should fail (first=%s, second=%s)",
            error1.get(), error2.get())
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

  // --- 440.12: Reversal tests ---

  @Test
  void reverseCreditTransaction_requiresApproval() {
    UUID[] depositTxnId = new UUID[1];

    // Create a deposit (credit type) and approve it
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Reversal Credit Client", "rev_credit@test.com", memberId));

                  var depositResponse =
                      transactionService.recordDeposit(
                          trustAccountId,
                          new RecordDepositRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("5000.00"),
                              "DEP-REV-001",
                              "Deposit to reverse",
                              LocalDate.of(2026, 3, 20)));

                  depositTxnId[0] = depositResponse.id();

                  // Manually set status to APPROVED (simulating approval flow from Epic 441)
                  var txn = transactionRepository.findById(depositTxnId[0]).orElseThrow();
                  txn.setStatus("APPROVED");
                  transactionRepository.save(txn);
                }));

    // Reverse the credit (deposit) — should require approval
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var reversalResponse =
                      transactionService.reverseTransaction(
                          depositTxnId[0], "Client overpaid, reversing deposit");

                  assertThat(reversalResponse.transactionType()).isEqualTo("REVERSAL");
                  assertThat(reversalResponse.status()).isEqualTo("AWAITING_APPROVAL");
                  assertThat(reversalResponse.reversalOf()).isEqualTo(depositTxnId[0]);
                  assertThat(reversalResponse.reference()).isEqualTo("REV-DEP-REV-001");
                  assertThat(reversalResponse.amount())
                      .isEqualByComparingTo(new BigDecimal("5000.00"));

                  // Original should still be APPROVED (not REVERSED yet) — marking as
                  // REVERSED is deferred to the approval flow (Epic 441) for credit reversals
                  var original = transactionRepository.findById(depositTxnId[0]).orElseThrow();
                  assertThat(original.getStatus()).isEqualTo("APPROVED");
                  assertThat(original.getReversedById()).isNull();

                  // Ledger should NOT be updated (credit reversal awaits approval)
                  var ledger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(
                              trustAccountId, original.getCustomerId())
                          .orElseThrow();

                  // Balance still has the deposit amount (5000) — not yet reduced
                  assertThat(ledger.getBalance()).isEqualByComparingTo(new BigDecimal("5000.00"));
                }));
  }

  @Test
  void reverseTransferOut_reversesBothLegsAndUpdatesBothLedgers() {
    UUID[] transferOutTxnId = new UUID[1];
    UUID[] transferInTxnId = new UUID[1];
    UUID[] sourceCustomerId = new UUID[1];
    UUID[] targetCustomerId = new UUID[1];

    // Create a deposit, then a transfer out (debit type), and approve the transfer
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var sourceCustomer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Reversal Debit Source", "rev_debit_src@test.com", memberId));

                  var targetCustomer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Reversal Debit Target", "rev_debit_tgt@test.com", memberId));

                  sourceCustomerId[0] = sourceCustomer.getId();
                  targetCustomerId[0] = targetCustomer.getId();

                  // Fund source with 10000
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          sourceCustomer.getId(),
                          null,
                          new BigDecimal("10000.00"),
                          "DEP-REV-002",
                          "Funding for debit reversal test",
                          LocalDate.of(2026, 3, 21)));

                  // Transfer out 3000 (debit from source)
                  var transferResult =
                      transactionService.recordTransfer(
                          trustAccountId,
                          new RecordTransferRequest(
                              sourceCustomer.getId(),
                              targetCustomer.getId(),
                              null,
                              new BigDecimal("3000.00"),
                              "TRF-REV-001",
                              "Transfer to reverse",
                              LocalDate.of(2026, 3, 22)));

                  // The TRANSFER_OUT is the first in the pair, TRANSFER_IN is second
                  transferOutTxnId[0] = transferResult.get(0).id();
                  transferInTxnId[0] = transferResult.get(1).id();

                  // Manually set TRANSFER_OUT status to APPROVED
                  var txn = transactionRepository.findById(transferOutTxnId[0]).orElseThrow();
                  txn.setStatus("APPROVED");
                  transactionRepository.save(txn);
                }));

    // Reverse the TRANSFER_OUT — should reverse both legs immediately
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Source balance before reversal: 10000 - 3000 = 7000
                  var sourceLedgerBefore =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, sourceCustomerId[0])
                          .orElseThrow();
                  assertThat(sourceLedgerBefore.getBalance())
                      .isEqualByComparingTo(new BigDecimal("7000.00"));

                  // Target balance before reversal: 3000 (received from transfer)
                  var targetLedgerBefore =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, targetCustomerId[0])
                          .orElseThrow();
                  assertThat(targetLedgerBefore.getBalance())
                      .isEqualByComparingTo(new BigDecimal("3000.00"));

                  var reversalResponse =
                      transactionService.reverseTransaction(
                          transferOutTxnId[0], "Transfer was in error");

                  assertThat(reversalResponse.transactionType()).isEqualTo("REVERSAL");
                  assertThat(reversalResponse.status()).isEqualTo("RECORDED");
                  assertThat(reversalResponse.reversalOf()).isEqualTo(transferOutTxnId[0]);

                  // Source ledger should be restored: 7000 + 3000 = 10000
                  var sourceLedgerAfter =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, sourceCustomerId[0])
                          .orElseThrow();
                  assertThat(sourceLedgerAfter.getBalance())
                      .isEqualByComparingTo(new BigDecimal("10000.00"));

                  // Target ledger should be debited: 3000 - 3000 = 0
                  var targetLedgerAfter =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, targetCustomerId[0])
                          .orElseThrow();
                  assertThat(targetLedgerAfter.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);

                  // The paired TRANSFER_IN should also be marked as REVERSED
                  var pairedIn = transactionRepository.findById(transferInTxnId[0]).orElseThrow();
                  assertThat(pairedIn.getStatus()).isEqualTo("REVERSED");
                  assertThat(pairedIn.getReversedById()).isNotNull();

                  // The original TRANSFER_OUT should be marked as REVERSED
                  var originalOut =
                      transactionRepository.findById(transferOutTxnId[0]).orElseThrow();
                  assertThat(originalOut.getStatus()).isEqualTo("REVERSED");
                  assertThat(originalOut.getReversedById()).isNotNull();
                }));
  }

  @Test
  void reverseTransferIn_throwsInvalidStateException() {
    UUID[] transferInTxnId = new UUID[1];

    // Create a deposit, then a transfer — we'll try to reverse the TRANSFER_IN directly
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var sourceCustomer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "TrfIn Rev Source", "trfin_rev_src@test.com", memberId));

                  var targetCustomer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "TrfIn Rev Target", "trfin_rev_tgt@test.com", memberId));

                  // Fund source
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          sourceCustomer.getId(),
                          null,
                          new BigDecimal("5000.00"),
                          "DEP-TRFIN-001",
                          null,
                          LocalDate.of(2026, 3, 28)));

                  var transferResult =
                      transactionService.recordTransfer(
                          trustAccountId,
                          new RecordTransferRequest(
                              sourceCustomer.getId(),
                              targetCustomer.getId(),
                              null,
                              new BigDecimal("2000.00"),
                              "TRF-TRFIN-001",
                              null,
                              LocalDate.of(2026, 3, 29)));

                  // TRANSFER_IN is the second in the pair
                  transferInTxnId[0] = transferResult.get(1).id();
                }));

    // Attempt to reverse the TRANSFER_IN directly — should fail
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionService.reverseTransaction(
                            transferInTxnId[0], "Trying to reverse TRANSFER_IN directly"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("TRANSFER_OUT"));
  }

  @Test
  void reverseAlreadyReversedTransaction_throwsInvalidStateException() {
    UUID[] depositTxnId = new UUID[1];

    // Create and approve a deposit, then reverse it
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Double Reversal Client", "double_rev@test.com", memberId));

                  var depositResponse =
                      transactionService.recordDeposit(
                          trustAccountId,
                          new RecordDepositRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("2000.00"),
                              "DEP-REV-003",
                              "Deposit for double reversal test",
                              LocalDate.of(2026, 3, 23)));

                  depositTxnId[0] = depositResponse.id();

                  // Approve then reverse
                  var txn = transactionRepository.findById(depositTxnId[0]).orElseThrow();
                  txn.setStatus("APPROVED");
                  transactionRepository.save(txn);

                  transactionService.reverseTransaction(depositTxnId[0], "First reversal");
                }));

    // Attempt to reverse the already-reversed transaction — should fail because a reversal
    // transaction already exists (detected via existsByReversalOf)
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionService.reverseTransaction(
                            depositTxnId[0], "Second reversal attempt"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("already been reversed"));
  }

  @Test
  void reverseAwaitingApprovalTransaction_throwsInvalidStateException() {
    UUID[] depositTxnId = new UUID[1];

    // Create a deposit that remains in RECORDED status (not APPROVED)
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Awaiting Reversal Client", "awaiting_rev@test.com", memberId));

                  var depositResponse =
                      transactionService.recordDeposit(
                          trustAccountId,
                          new RecordDepositRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("1500.00"),
                              "DEP-REV-004",
                              "Deposit for awaiting reversal test",
                              LocalDate.of(2026, 3, 24)));

                  depositTxnId[0] = depositResponse.id();

                  // Set to AWAITING_APPROVAL to test this specific case
                  var txn = transactionRepository.findById(depositTxnId[0]).orElseThrow();
                  txn.setStatus("AWAITING_APPROVAL");
                  transactionRepository.save(txn);
                }));

    // Attempt to reverse — should fail because it's not APPROVED
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionService.reverseTransaction(
                            depositTxnId[0], "Cannot reverse awaiting approval"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("APPROVED status"));
  }

  // --- 440.13: Cashbook balance tests ---

  @Test
  void cashbookBalance_correctAfterDepositsAndTransfers() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Capture baseline before this test's transactions
                  var balanceBefore =
                      transactionService.getCashbookBalance(trustAccountId).balance();

                  var customer1 =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Cashbook Client 1", "cashbook1@test.com", memberId));

                  var customer2 =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Cashbook Client 2", "cashbook2@test.com", memberId));

                  // Deposit 10000 to customer1 (cashbook positive)
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer1.getId(),
                          null,
                          new BigDecimal("10000.00"),
                          "DEP-CB-001",
                          "Cashbook deposit 1",
                          LocalDate.of(2026, 3, 25)));

                  // Deposit 5000 to customer2 (cashbook positive)
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer2.getId(),
                          null,
                          new BigDecimal("5000.00"),
                          "DEP-CB-002",
                          "Cashbook deposit 2",
                          LocalDate.of(2026, 3, 25)));

                  // Transfer 2000 from customer1 to customer2 (cashbook neutral)
                  transactionService.recordTransfer(
                      trustAccountId,
                      new RecordTransferRequest(
                          customer1.getId(),
                          customer2.getId(),
                          null,
                          new BigDecimal("2000.00"),
                          "TRF-CB-001",
                          "Cashbook transfer",
                          LocalDate.of(2026, 3, 26)));

                  // Cashbook balance delta should be 10000 + 5000 = 15000
                  // Transfers are cashbook-neutral (no cashbook effect)
                  var balanceAfter =
                      transactionService.getCashbookBalance(trustAccountId).balance();
                  var delta = balanceAfter.subtract(balanceBefore);

                  assertThat(delta).isEqualByComparingTo(new BigDecimal("15000.00"));
                }));
  }

  @Test
  void cashbookBalance_excludesRejectedTransactions() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Capture baseline
                  var balanceBefore =
                      transactionService.getCashbookBalance(trustAccountId).balance();

                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Cashbook Rejected Client", "cashbook_rej@test.com", memberId));

                  // Record a deposit (RECORDED status, counted in cashbook)
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("8000.00"),
                          "DEP-CB-003",
                          "Normal deposit",
                          LocalDate.of(2026, 3, 27)));

                  // Create another deposit and reject it
                  var depositToReject =
                      transactionService.recordDeposit(
                          trustAccountId,
                          new RecordDepositRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("3000.00"),
                              "DEP-CB-004",
                              "Rejected deposit",
                              LocalDate.of(2026, 3, 27)));

                  // Set the second deposit to REJECTED
                  var rejectedTxn =
                      transactionRepository.findById(depositToReject.id()).orElseThrow();
                  rejectedTxn.setStatus("REJECTED");
                  transactionRepository.save(rejectedTxn);

                  // Cashbook delta should only include the first deposit (8000),
                  // not the rejected one (3000)
                  var balanceAfter =
                      transactionService.getCashbookBalance(trustAccountId).balance();
                  var delta = balanceAfter.subtract(balanceBefore);

                  assertThat(delta)
                      .as("Rejected deposit should be excluded from cashbook balance")
                      .isEqualByComparingTo(new BigDecimal("8000.00"));
                }));
  }

  // --- 440.14: Historical balance tests ---

  @Test
  void historicalBalance_matchesExpectedAtPointInTime() {
    UUID[] customerId = new UUID[1];

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Historical Balance Client", "hist_bal@test.com", memberId));
                  customerId[0] = customer.getId();

                  // Deposit 10000 on March 1
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("10000.00"),
                          "DEP-HIST-001",
                          "March deposit",
                          LocalDate.of(2026, 3, 1)));

                  // Deposit 5000 on March 15
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("5000.00"),
                          "DEP-HIST-002",
                          "Mid-March deposit",
                          LocalDate.of(2026, 3, 15)));
                }));

    // Check historical balance as of March 10 — should only include the first deposit
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var balanceMarch10 =
                      clientLedgerService.getClientBalanceAsOfDate(
                          customerId[0], trustAccountId, LocalDate.of(2026, 3, 10));

                  assertThat(balanceMarch10).isEqualByComparingTo(new BigDecimal("10000.00"));

                  // Check as of March 31 — should include both deposits
                  var balanceMarch31 =
                      clientLedgerService.getClientBalanceAsOfDate(
                          customerId[0], trustAccountId, LocalDate.of(2026, 3, 31));

                  assertThat(balanceMarch31).isEqualByComparingTo(new BigDecimal("15000.00"));
                }));
  }

  @Test
  void totalTrustBalance_equalsSumOfLedgerCardBalances() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer1 =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Total Bal Client 1", "total_bal1@test.com", memberId));

                  var customer2 =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Total Bal Client 2", "total_bal2@test.com", memberId));

                  // Deposit 6000 to customer1
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer1.getId(),
                          null,
                          new BigDecimal("6000.00"),
                          "DEP-TOT-001",
                          null,
                          LocalDate.of(2026, 3, 28)));

                  // Deposit 4000 to customer2
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer2.getId(),
                          null,
                          new BigDecimal("4000.00"),
                          "DEP-TOT-002",
                          null,
                          LocalDate.of(2026, 3, 28)));

                  // Get total trust balance from ledger cards
                  var totalBalanceResponse =
                      clientLedgerService.getTotalTrustBalance(trustAccountId);
                  var totalBalance = totalBalanceResponse.balance();

                  // Get individual ledger card balances and sum them
                  var ledger1 =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, customer1.getId())
                          .orElseThrow();
                  var ledger2 =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, customer2.getId())
                          .orElseThrow();

                  var expectedTotal = ledger1.getBalance().add(ledger2.getBalance());

                  // Total trust balance should include these two plus any other existing cards
                  assertThat(totalBalance).isGreaterThanOrEqualTo(expectedTotal);
                  // More specifically, these two cards contribute at least 10000
                  assertThat(totalBalance).isGreaterThanOrEqualTo(new BigDecimal("10000.00"));
                }));
  }

  // --- 441.6: Approval Workflow Tests ---

  @Test
  void payment_createdInAwaitingApproval() {
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Payment Approval Client", "pay_approval@test.com", memberId));

                  // Fund the account first
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("20000.00"),
                          "DEP-PAY-001",
                          "Funding for payment test",
                          LocalDate.of(2026, 4, 1)));

                  var response =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("5000.00"),
                              "PAY-001",
                              "Payment to supplier",
                              LocalDate.of(2026, 4, 1)));

                  assertThat(response.id()).isNotNull();
                  assertThat(response.transactionType()).isEqualTo("PAYMENT");
                  assertThat(response.status()).isEqualTo("AWAITING_APPROVAL");
                  assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("5000.00"));
                  assertThat(response.recordedBy()).isEqualTo(memberId);
                }));
  }

  @Test
  void approvePayment_transitionsToApprovedAndDebitsLedger() {
    UUID[] txnId = new UUID[1];
    UUID[] customerId = new UUID[1];

    // Record the payment as the recorder (memberId)
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Approve Payment Client", "approve_pay@test.com", memberId));
                  customerId[0] = customer.getId();

                  // Fund the account
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("10000.00"),
                          "DEP-PAY-002",
                          "Funding",
                          LocalDate.of(2026, 4, 2)));

                  var response =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("3000.00"),
                              "PAY-002",
                              "Payment for approval",
                              LocalDate.of(2026, 4, 2)));

                  txnId[0] = response.id();
                }));

    // Approve as a different member (approverId)
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response = transactionService.approveTransaction(txnId[0], approverId);

                  assertThat(response.status()).isEqualTo("APPROVED");
                  assertThat(response.approvedBy()).isEqualTo(approverId);
                  assertThat(response.approvedAt()).isNotNull();

                  // Verify ledger was debited
                  var ledger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, customerId[0])
                          .orElseThrow();

                  // 10000 deposit - 3000 payment = 7000
                  assertThat(ledger.getBalance()).isEqualByComparingTo(new BigDecimal("7000.00"));
                  assertThat(ledger.getTotalPayments())
                      .isEqualByComparingTo(new BigDecimal("3000.00"));
                }));
  }

  @Test
  void selfApproval_returns400() {
    UUID[] txnId = new UUID[1];

    // Record the payment as memberId
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Self Approve Client", "self_approve@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("10000.00"),
                          "DEP-PAY-003",
                          "Funding",
                          LocalDate.of(2026, 4, 3)));

                  var response =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("1000.00"),
                              "PAY-003",
                              "Self-approval test",
                              LocalDate.of(2026, 4, 3)));

                  txnId[0] = response.id();
                }));

    // Try to approve as the same member who recorded it
    runInTenantWithCapabilities(
        () ->
            assertThatThrownBy(() -> transactionService.approveTransaction(txnId[0], memberId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("The transaction recorder cannot be the sole approver"));
  }

  @Test
  void approveWithInsufficientBalance_returns400WithClearMessage() {
    UUID[] txnId = new UUID[1];

    // Record a payment larger than the balance
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Insufficient Approve Client", "insuff_approve@test.com", memberId));

                  // Fund with only 500
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("500.00"),
                          "DEP-PAY-004",
                          "Small funding",
                          LocalDate.of(2026, 4, 4)));

                  var response =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("5000.00"),
                              "PAY-004",
                              "Too large payment",
                              LocalDate.of(2026, 4, 4)));

                  txnId[0] = response.id();
                }));

    // Approve as a different member — should fail due to insufficient balance
    runAsApprover(
        () ->
            assertThatThrownBy(() -> transactionService.approveTransaction(txnId[0], approverId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Insufficient trust balance")
                .hasMessageContaining("Available: R")
                .hasMessageContaining("Requested: R"));
  }

  @Test
  void rejectTransaction_transitionsToRejectedWithNoLedgerEffect() {
    UUID[] txnId = new UUID[1];
    UUID[] customerId = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Reject Payment Client", "reject_pay@test.com", memberId));
                  customerId[0] = customer.getId();

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("8000.00"),
                          "DEP-PAY-005",
                          "Funding",
                          LocalDate.of(2026, 4, 5)));

                  var response =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("2000.00"),
                              "PAY-005",
                              "Payment to reject",
                              LocalDate.of(2026, 4, 5)));

                  txnId[0] = response.id();
                }));

    // Reject as a different member
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response =
                      transactionService.rejectTransaction(
                          txnId[0], approverId, "Duplicate payment");

                  assertThat(response.status()).isEqualTo("REJECTED");
                  assertThat(response.rejectedBy()).isEqualTo(approverId);
                  assertThat(response.rejectedAt()).isNotNull();
                  assertThat(response.rejectionReason()).isEqualTo("Duplicate payment");

                  // Verify ledger was NOT debited (balance should remain 8000)
                  var ledger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(trustAccountId, customerId[0])
                          .orElseThrow();

                  assertThat(ledger.getBalance()).isEqualByComparingTo(new BigDecimal("8000.00"));
                }));
  }

  @Test
  void approveNonAwaitingTransaction_returns400() {
    UUID[] txnId = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Already Approved Client", "already_approved@test.com", memberId));

                  // Record and manually set to APPROVED
                  var response =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("1000.00"),
                              "PAY-006",
                              "Already approved",
                              LocalDate.of(2026, 4, 6)));

                  txnId[0] = response.id();

                  var txn = transactionRepository.findById(txnId[0]).orElseThrow();
                  txn.setStatus("APPROVED");
                  transactionRepository.save(txn);
                }));

    // Attempt to approve an already-APPROVED transaction
    runAsApprover(
        () ->
            assertThatThrownBy(() -> transactionService.approveTransaction(txnId[0], approverId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("AWAITING_APPROVAL"));
  }

  @Test
  void feeTransfer_createdWithInvoiceId() {
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Fee Transfer Client", "fee_transfer@test.com", memberId));

                  // Fund the account
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("15000.00"),
                          "DEP-FT-001",
                          "Funding for fee transfer",
                          LocalDate.of(2026, 4, 7)));

                  // Create an invoice in APPROVED status using native SQL
                  var invoiceId = UUID.randomUUID();
                  entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoices (id, customer_id, invoice_number, status,
                              subtotal, tax_amount, total, currency, due_date,
                              customer_name, org_name, created_by, created_at, updated_at)
                          VALUES (:id, :customerId, 'INV-FT-001', 'APPROVED',
                              1000.00, 150.00, 1150.00, 'ZAR', :dueDate,
                              'Fee Transfer Client', 'Test Org', :createdBy, now(), now())
                          """)
                      .setParameter("id", invoiceId)
                      .setParameter("customerId", customer.getId())
                      .setParameter("dueDate", LocalDate.of(2026, 5, 1))
                      .setParameter("createdBy", memberId)
                      .executeUpdate();

                  entityManager.flush();

                  var response =
                      transactionService.recordFeeTransfer(
                          trustAccountId,
                          new RecordFeeTransferRequest(
                              customer.getId(), invoiceId, new BigDecimal("1150.00"), "FT-001"));

                  assertThat(response.id()).isNotNull();
                  assertThat(response.transactionType()).isEqualTo("FEE_TRANSFER");
                  assertThat(response.status()).isEqualTo("AWAITING_APPROVAL");
                  assertThat(response.invoiceId()).isEqualTo(invoiceId);
                  assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("1150.00"));
                }));
  }

  @Test
  void refund_createdInAwaitingApproval() {
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Refund Client", "refund@test.com", memberId));

                  // Fund the account
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("5000.00"),
                          "DEP-REF-001",
                          "Funding for refund test",
                          LocalDate.of(2026, 4, 8)));

                  var response =
                      transactionService.recordRefund(
                          trustAccountId,
                          new RecordRefundRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("2000.00"),
                              "REF-001",
                              "Refund to client",
                              LocalDate.of(2026, 4, 8)));

                  assertThat(response.id()).isNotNull();
                  assertThat(response.transactionType()).isEqualTo("REFUND");
                  assertThat(response.status()).isEqualTo("AWAITING_APPROVAL");
                  assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("2000.00"));
                  assertThat(response.recordedBy()).isEqualTo(memberId);
                }));
  }

  @Test
  void approvePayment_emitsAuditEvent() {
    UUID[] txnId = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Audit Approve Client", "audit_approve@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("10000.00"),
                          "DEP-PAY-009",
                          "Funding",
                          LocalDate.of(2026, 4, 9)));

                  var response =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("1000.00"),
                              "PAY-009",
                              "Audit approval test",
                              LocalDate.of(2026, 4, 9)));

                  txnId[0] = response.id();
                }));

    // Approve and check audit count increases
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  long auditCountBefore = auditEventRepository.count();

                  transactionService.approveTransaction(txnId[0], approverId);

                  long auditCountAfter = auditEventRepository.count();
                  assertThat(auditCountAfter).isGreaterThan(auditCountBefore);
                }));
  }

  @Test
  void rejection_includesReasonInResponse() {
    UUID[] txnId = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Rejection Reason Client", "reject_reason@test.com", memberId));

                  var response =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("500.00"),
                              "PAY-010",
                              "Rejection reason test",
                              LocalDate.of(2026, 4, 10)));

                  txnId[0] = response.id();
                }));

    // Reject with a specific reason
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response =
                      transactionService.rejectTransaction(
                          txnId[0], approverId, "Amount does not match invoice");

                  assertThat(response.status()).isEqualTo("REJECTED");
                  assertThat(response.rejectionReason()).isEqualTo("Amount does not match invoice");
                  assertThat(response.rejectedBy()).isEqualTo(approverId);
                  assertThat(response.rejectedAt()).isNotNull();
                }));
  }

  // --- 441.11: Dual Approval Integration Tests ---

  @Test
  void dualApproval_firstApproval_setsApprovedByAndKeepsAwaitingApproval() {
    UUID[] txnId = new UUID[1];

    // Record a payment on the dual-approval account as memberId
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Dual First Client", "dual_first@test.com", memberId));

                  transactionService.recordDeposit(
                      dualApprovalAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("20000.00"),
                          "DEP-DUAL-001",
                          "Funding",
                          LocalDate.of(2026, 4, 1)));

                  var response =
                      transactionService.recordPayment(
                          dualApprovalAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("5000.00"),
                              "PAY-DUAL-001",
                              "Dual approval test",
                              LocalDate.of(2026, 4, 1)));

                  txnId[0] = response.id();
                }));

    // First approval by approverId
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response = transactionService.approveTransaction(txnId[0], approverId);

                  assertThat(response.status()).isEqualTo("AWAITING_APPROVAL");
                  assertThat(response.approvedBy()).isEqualTo(approverId);
                  assertThat(response.approvedAt()).isNotNull();
                  assertThat(response.secondApprovedBy()).isNull();
                }));
  }

  @Test
  void dualApproval_secondApproval_completesAndDebitsLedger() {
    UUID[] txnId = new UUID[1];
    UUID[] customerId = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Dual Complete Client", "dual_complete@test.com", memberId));
                  customerId[0] = customer.getId();

                  transactionService.recordDeposit(
                      dualApprovalAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("20000.00"),
                          "DEP-DUAL-002",
                          "Funding",
                          LocalDate.of(2026, 4, 1)));

                  var response =
                      transactionService.recordPayment(
                          dualApprovalAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("5000.00"),
                              "PAY-DUAL-002",
                              "Dual complete test",
                              LocalDate.of(2026, 4, 1)));

                  txnId[0] = response.id();
                }));

    // First approval
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> transactionService.approveTransaction(txnId[0], approverId)));

    // Second approval by a different approver
    runAsSecondApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response = transactionService.approveTransaction(txnId[0], secondApproverId);

                  assertThat(response.status()).isEqualTo("APPROVED");
                  assertThat(response.approvedBy()).isEqualTo(approverId);
                  assertThat(response.secondApprovedBy()).isEqualTo(secondApproverId);
                  assertThat(response.secondApprovedAt()).isNotNull();

                  // Verify ledger was debited
                  var ledger =
                      ledgerCardRepository
                          .findByTrustAccountIdAndCustomerId(dualApprovalAccountId, customerId[0])
                          .orElseThrow();
                  assertThat(ledger.getBalance()).isEqualByComparingTo(new BigDecimal("15000.00"));
                }));
  }

  @Test
  void dualApproval_samePersonAsBothApprovers_rejected() {
    UUID[] txnId = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Dual Same Client", "dual_same@test.com", memberId));

                  transactionService.recordDeposit(
                      dualApprovalAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("20000.00"),
                          "DEP-DUAL-003",
                          "Funding",
                          LocalDate.of(2026, 4, 1)));

                  var response =
                      transactionService.recordPayment(
                          dualApprovalAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("5000.00"),
                              "PAY-DUAL-003",
                              "Dual same person test",
                              LocalDate.of(2026, 4, 1)));

                  txnId[0] = response.id();
                }));

    // First approval by approverId
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> transactionService.approveTransaction(txnId[0], approverId)));

    // Second approval by same approverId — should be rejected
    runAsApprover(
        () ->
            assertThatThrownBy(() -> transactionService.approveTransaction(txnId[0], approverId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("different from the first approver"));
  }

  @Test
  void dualApproval_recorderAsFirstApproverDifferentSecond_succeeds() {
    UUID[] txnId = new UUID[1];

    // Record a payment as approverId (who will also be the first approver)
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Dual Recorder Client", "dual_recorder@test.com", approverId));

                  transactionService.recordDeposit(
                      dualApprovalAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("20000.00"),
                          "DEP-DUAL-004",
                          "Funding",
                          LocalDate.of(2026, 4, 1)));

                  var response =
                      transactionService.recordPayment(
                          dualApprovalAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("5000.00"),
                              "PAY-DUAL-004",
                              "Recorder as first approver",
                              LocalDate.of(2026, 4, 1)));

                  txnId[0] = response.id();
                }));

    // First approval by recorder (approverId) — allowed in dual mode
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response = transactionService.approveTransaction(txnId[0], approverId);
                  assertThat(response.status()).isEqualTo("AWAITING_APPROVAL");
                  assertThat(response.approvedBy()).isEqualTo(approverId);
                }));

    // Second approval by different person
    runAsSecondApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response = transactionService.approveTransaction(txnId[0], secondApproverId);
                  assertThat(response.status()).isEqualTo("APPROVED");
                  assertThat(response.secondApprovedBy()).isEqualTo(secondApproverId);
                }));
  }

  @Test
  void threshold_belowThreshold_usesSingleApproval() {
    UUID[] txnId = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Threshold Below Client", "threshold_below@test.com", memberId));

                  transactionService.recordDeposit(
                      thresholdAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("20000.00"),
                          "DEP-THR-001",
                          "Funding",
                          LocalDate.of(2026, 4, 1)));

                  // Amount R5,000 < threshold R10,000 — single approval should suffice
                  var response =
                      transactionService.recordPayment(
                          thresholdAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("5000.00"),
                              "PAY-THR-001",
                              "Below threshold test",
                              LocalDate.of(2026, 4, 1)));

                  txnId[0] = response.id();
                }));

    // Single approval should complete immediately (no dual required)
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response = transactionService.approveTransaction(txnId[0], approverId);

                  assertThat(response.status()).isEqualTo("APPROVED");
                  assertThat(response.approvedBy()).isEqualTo(approverId);
                  assertThat(response.secondApprovedBy()).isNull();
                }));
  }

  @Test
  void threshold_atThreshold_requiresDualApproval() {
    UUID[] txnId = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Threshold At Client", "threshold_at@test.com", memberId));

                  transactionService.recordDeposit(
                      thresholdAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("50000.00"),
                          "DEP-THR-002",
                          "Funding",
                          LocalDate.of(2026, 4, 1)));

                  // Amount R10,000 == threshold R10,000 — dual approval required
                  var response =
                      transactionService.recordPayment(
                          thresholdAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("10000.00"),
                              "PAY-THR-002",
                              "At threshold test",
                              LocalDate.of(2026, 4, 1)));

                  txnId[0] = response.id();
                }));

    // First approval — status should remain AWAITING_APPROVAL (dual mode)
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response = transactionService.approveTransaction(txnId[0], approverId);

                  assertThat(response.status()).isEqualTo("AWAITING_APPROVAL");
                  assertThat(response.approvedBy()).isEqualTo(approverId);
                }));

    // Second approval — should complete
    runAsSecondApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response = transactionService.approveTransaction(txnId[0], secondApproverId);

                  assertThat(response.status()).isEqualTo("APPROVED");
                  assertThat(response.secondApprovedBy()).isEqualTo(secondApproverId);
                }));
  }

  // --- 441.12: Concurrent Approval and Fee Transfer Tests ---

  @Test
  void concurrentApprovals_secondSeesUpdatedBalance() throws Exception {
    UUID[] txnId1 = new UUID[1];
    UUID[] txnId2 = new UUID[1];

    // Create two payments from same customer on the single-approval account
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Concurrent Client", "concurrent@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("8000.00"),
                          "DEP-CONC-001",
                          "Funding",
                          LocalDate.of(2026, 4, 2)));

                  var resp1 =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("5000.00"),
                              "PAY-CONC-001",
                              "First concurrent",
                              LocalDate.of(2026, 4, 2)));
                  txnId1[0] = resp1.id();

                  var resp2 =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("5000.00"),
                              "PAY-CONC-002",
                              "Second concurrent",
                              LocalDate.of(2026, 4, 2)));
                  txnId2[0] = resp2.id();
                }));

    // Approve first — should succeed (8000 >= 5000)
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response = transactionService.approveTransaction(txnId1[0], approverId);
                  assertThat(response.status()).isEqualTo("APPROVED");
                }));

    // Approve second — should fail (8000 - 5000 = 3000 < 5000)
    runAsApprover(
        () ->
            assertThatThrownBy(() -> transactionService.approveTransaction(txnId2[0], approverId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Insufficient trust balance"));
  }

  @Test
  void feeTransferApproval_marksInvoiceAsPaid() {
    UUID[] txnId = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "FT Invoice Client", "ft_invoice@test.com", memberId));

                  // Fund the account
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("50000.00"),
                          "DEP-FT-INV-001",
                          "Funding",
                          LocalDate.of(2026, 4, 2)));

                  // Create a SENT invoice via native SQL (simulating invoice lifecycle)
                  var invoiceId = UUID.randomUUID();
                  entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoices (id, customer_id, invoice_number, status, currency,
                              subtotal, tax_amount, total, due_date,
                              customer_name, org_name, created_by, created_at, updated_at)
                          VALUES (:id, :customerId, 'INV-FT-B-001', 'SENT', 'ZAR',
                              1000.00, 150.00, 1150.00, '2026-04-30',
                              'FT Invoice Client', 'Test Org', :memberId,
                              now(), now())
                          """)
                      .setParameter("id", invoiceId)
                      .setParameter("customerId", customer.getId())
                      .setParameter("memberId", memberId)
                      .executeUpdate();
                  entityManager.flush();

                  var response =
                      transactionService.recordFeeTransfer(
                          trustAccountId,
                          new RecordFeeTransferRequest(
                              customer.getId(),
                              invoiceId,
                              new BigDecimal("1150.00"),
                              "FT-INV-001"));

                  txnId[0] = response.id();
                }));

    // Approve the fee transfer — should mark invoice as PAID
    runAsApprover(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var response = transactionService.approveTransaction(txnId[0], approverId);

                  assertThat(response.status()).isEqualTo("APPROVED");

                  // Verify the invoice was marked as PAID
                  var invoice = invoiceRepository.findById(response.invoiceId()).orElseThrow();
                  assertThat(invoice.getStatus().name()).isEqualTo("PAID");
                }));
  }

  @Test
  void feeTransferApproval_alreadyPaidInvoice_fails() {
    UUID[] txnId = new UUID[1];

    // Create a SENT invoice, record fee transfer, then mark invoice PAID before approval
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "FT Paid Client", "ft_paid@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("50000.00"),
                          "DEP-FT-PAID-001",
                          "Funding",
                          LocalDate.of(2026, 4, 2)));

                  var invoiceId = UUID.randomUUID();
                  entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoices (id, customer_id, invoice_number, status, currency,
                              subtotal, tax_amount, total, due_date,
                              customer_name, org_name, created_by, created_at, updated_at)
                          VALUES (:id, :customerId, 'INV-FT-PAID-001', 'SENT', 'ZAR',
                              1000.00, 150.00, 1150.00, '2026-04-30',
                              'FT Paid Client', 'Test Org', :memberId,
                              now(), now())
                          """)
                      .setParameter("id", invoiceId)
                      .setParameter("customerId", customer.getId())
                      .setParameter("memberId", memberId)
                      .executeUpdate();
                  entityManager.flush();

                  var response =
                      transactionService.recordFeeTransfer(
                          trustAccountId,
                          new RecordFeeTransferRequest(
                              customer.getId(),
                              invoiceId,
                              new BigDecimal("1150.00"),
                              "FT-PAID-001"));

                  txnId[0] = response.id();

                  // Mark the invoice as PAID directly (simulating external payment)
                  entityManager
                      .createNativeQuery("UPDATE invoices SET status = 'PAID' WHERE id = :id")
                      .setParameter("id", invoiceId)
                      .executeUpdate();
                  entityManager.flush();
                }));

    // Approve — should fail because the invoice is already PAID
    runAsApprover(
        () ->
            assertThatThrownBy(() -> transactionService.approveTransaction(txnId[0], approverId))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("invoice is already in PAID status"));
  }

  @Test
  void notificationHandler_sendsOnAwaitingApproval() {
    // This test verifies that recording a payment (which sets AWAITING_APPROVAL)
    // results in notifications being created via the event handler.
    // Since @TransactionalEventListener runs AFTER_COMMIT, we need to let the
    // transaction commit before checking notifications.

    UUID[] txnId = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Notification Client", "notification@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("10000.00"),
                          "DEP-NOTIF-001",
                          "Funding",
                          LocalDate.of(2026, 4, 2)));

                  var response =
                      transactionService.recordPayment(
                          trustAccountId,
                          new RecordPaymentRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("1000.00"),
                              "PAY-NOTIF-001",
                              "Notification test",
                              LocalDate.of(2026, 4, 2)));

                  txnId[0] = response.id();
                }));

    // After commit, the @TransactionalEventListener should fire.
    // Give it a moment and check notifications were created.
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var notifications = notificationRepository.findAll();
                  var trustNotifications =
                      notifications.stream()
                          .filter(n -> "TRUST_PAYMENT_AWAITING_APPROVAL".equals(n.getType()))
                          .toList();
                  // At least one notification should have been created for approvers
                  assertThat(trustNotifications).isNotEmpty();
                }));
  }

  // --- GAP-L-69: matter context inference / pass-through ---

  /**
   * Inserts a project row directly so the trust_transactions.project_id FK constraint is satisfied
   * (`trust_transactions_project_id_fkey` -> projects.id, V85 trust accounting tables).
   */
  private void insertProject(UUID projectId, String name) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO projects (id, name, description, status, created_by, created_at, updated_at)
            VALUES (:id, :name, 'L69 test', 'ACTIVE', :memberId, now(), now())
            """)
        .setParameter("id", projectId)
        .setParameter("name", name)
        .setParameter("memberId", memberId)
        .executeUpdate();
  }

  @Test
  void recordFeeTransfer_singleMatterInvoice_inheritsProjectIdFromInvoiceLine() {
    UUID projectId = UUID.randomUUID();
    UUID[] txnId = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  insertProject(projectId, "L69 Single Matter");

                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "L69 Single Matter Client", "l69_single@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          projectId,
                          new BigDecimal("10000.00"),
                          "DEP-L69-SINGLE",
                          "Funding",
                          LocalDate.of(2026, 4, 25)));

                  var invoiceId = UUID.randomUUID();
                  entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoices (id, customer_id, invoice_number, status, currency,
                              subtotal, tax_amount, total, due_date,
                              customer_name, org_name, created_by, created_at, updated_at)
                          VALUES (:id, :customerId, 'INV-L69-SINGLE', 'SENT', 'ZAR',
                              1000.00, 150.00, 1150.00, '2026-05-30',
                              'L69 Single Matter Client', 'Test Org', :memberId,
                              now(), now())
                          """)
                      .setParameter("id", invoiceId)
                      .setParameter("customerId", customer.getId())
                      .setParameter("memberId", memberId)
                      .executeUpdate();

                  // Single invoice line bound to the single matter
                  entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoice_lines (id, invoice_id, project_id, line_type,
                              description, quantity, unit_price, amount, sort_order, tax_exempt,
                              created_at, updated_at)
                          VALUES (gen_random_uuid(), :invoiceId, :projectId, 'TIME',
                              'Legal services', 1, 1150.00, 1150.00, 0, false,
                              now(), now())
                          """)
                      .setParameter("invoiceId", invoiceId)
                      .setParameter("projectId", projectId)
                      .executeUpdate();
                  entityManager.flush();

                  var response =
                      transactionService.recordFeeTransfer(
                          trustAccountId,
                          new RecordFeeTransferRequest(
                              customer.getId(),
                              invoiceId,
                              new BigDecimal("1150.00"),
                              "FT-L69-SINGLE"));

                  txnId[0] = response.id();
                  assertThat(response.projectId())
                      .as(
                          "FEE_TRANSFER projectId should be inferred from the invoice's single line")
                      .isEqualTo(projectId);
                }));

    // Confirm persisted row carries projectId for the closure-gate query
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var saved = transactionRepository.findById(txnId[0]).orElseThrow();
                  assertThat(saved.getProjectId()).isEqualTo(projectId);
                }));
  }

  @Test
  void recordFeeTransfer_multiMatterInvoice_leavesProjectIdNull() {
    UUID projectIdA = UUID.randomUUID();
    UUID projectIdB = UUID.randomUUID();

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  insertProject(projectIdA, "L69 Multi Matter A");
                  insertProject(projectIdB, "L69 Multi Matter B");

                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "L69 Multi Matter Client", "l69_multi@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          projectIdA,
                          new BigDecimal("10000.00"),
                          "DEP-L69-MULTI",
                          "Funding",
                          LocalDate.of(2026, 4, 25)));

                  var invoiceId = UUID.randomUUID();
                  entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoices (id, customer_id, invoice_number, status, currency,
                              subtotal, tax_amount, total, due_date,
                              customer_name, org_name, created_by, created_at, updated_at)
                          VALUES (:id, :customerId, 'INV-L69-MULTI', 'SENT', 'ZAR',
                              2000.00, 300.00, 2300.00, '2026-05-30',
                              'L69 Multi Matter Client', 'Test Org', :memberId,
                              now(), now())
                          """)
                      .setParameter("id", invoiceId)
                      .setParameter("customerId", customer.getId())
                      .setParameter("memberId", memberId)
                      .executeUpdate();

                  // Two lines bound to different matters — multi-matter case
                  entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoice_lines (id, invoice_id, project_id, line_type,
                              description, quantity, unit_price, amount, sort_order, tax_exempt,
                              created_at, updated_at)
                          VALUES (gen_random_uuid(), :invoiceId, :projectId, 'TIME',
                              'Matter A work', 1, 1150.00, 1150.00, 0, false,
                              now(), now())
                          """)
                      .setParameter("invoiceId", invoiceId)
                      .setParameter("projectId", projectIdA)
                      .executeUpdate();
                  entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoice_lines (id, invoice_id, project_id, line_type,
                              description, quantity, unit_price, amount, sort_order, tax_exempt,
                              created_at, updated_at)
                          VALUES (gen_random_uuid(), :invoiceId, :projectId, 'TIME',
                              'Matter B work', 1, 1150.00, 1150.00, 1, false,
                              now(), now())
                          """)
                      .setParameter("invoiceId", invoiceId)
                      .setParameter("projectId", projectIdB)
                      .executeUpdate();
                  entityManager.flush();

                  var response =
                      transactionService.recordFeeTransfer(
                          trustAccountId,
                          new RecordFeeTransferRequest(
                              customer.getId(),
                              invoiceId,
                              new BigDecimal("2300.00"),
                              "FT-L69-MULTI"));

                  assertThat(response.projectId())
                      .as("FEE_TRANSFER projectId should be null when invoice spans matters")
                      .isNull();
                }));
  }

  @Test
  void recordRefund_withProjectId_persistsProjectId() {
    UUID projectId = UUID.randomUUID();
    UUID[] txnId = new UUID[1];

    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  insertProject(projectId, "L69 Refund Matter");

                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "L69 Refund Matter Client", "l69_refund_matter@test.com", memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          projectId,
                          new BigDecimal("5000.00"),
                          "DEP-L69-REF-MATTER",
                          "Funding",
                          LocalDate.of(2026, 4, 25)));

                  var response =
                      transactionService.recordRefund(
                          trustAccountId,
                          new RecordRefundRequest(
                              customer.getId(),
                              projectId,
                              new BigDecimal("2000.00"),
                              "REF-L69-MATTER",
                              "Refund residual on closure",
                              LocalDate.of(2026, 4, 25)));

                  txnId[0] = response.id();
                  assertThat(response.projectId())
                      .as("REFUND projectId should be carried through from the request")
                      .isEqualTo(projectId);
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var saved = transactionRepository.findById(txnId[0]).orElseThrow();
                  assertThat(saved.getProjectId()).isEqualTo(projectId);
                }));
  }

  @Test
  void recordRefund_withoutProjectId_persistsNullProjectId() {
    runInTenantWithCapabilities(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "L69 Refund No Matter Client",
                              "l69_refund_nomatter@test.com",
                              memberId));

                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          null,
                          new BigDecimal("5000.00"),
                          "DEP-L69-REF-NOMATTER",
                          "Funding",
                          LocalDate.of(2026, 4, 25)));

                  var response =
                      transactionService.recordRefund(
                          trustAccountId,
                          new RecordRefundRequest(
                              customer.getId(),
                              null,
                              new BigDecimal("1000.00"),
                              "REF-L69-NOMATTER",
                              "Customer-level refund",
                              LocalDate.of(2026, 4, 25)));

                  assertThat(response.projectId())
                      .as("REFUND projectId should remain null when omitted (back-compat)")
                      .isNull();
                }));
  }

  // --- Helpers ---

  private void runAsSecondApprover(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, secondApproverId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"))
        .run(action);
  }

  private void runAsApprover(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, approverId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"))
        .run(action);
  }

  private void runInTenantWithCapabilities(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"))
        .run(action);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}

package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.collections.CollectionsReadService;
import io.b2mash.b2b.b2bstrawman.collections.CollectionsReadService.OutstandingInvoice;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordFeeTransferRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordRefundRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordTransferRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G88-F48: server-dated trust postings (fee transfer, reversals, account open/close) must be dated
 * in South African time. The clock is pinned at 2026-10-29T23:30Z, which is 01:30 SAST on
 * 2026-10-30 — a UTC "today" would date these postings a day early, before the deposit that funds
 * them.
 *
 * <p>The same pinned clock also proves the collections {@code :today} binding (it reaches every
 * service in this context, so no second Spring context is needed): days overdue are counted from
 * the SA date, not the UTC date or the DB {@code CURRENT_DATE}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustPostingDateZoneIntegrationTest {

  private static final String ORG_ID = "org_trust_posting_date_zone";
  private static final Instant BOUNDARY = Instant.parse("2026-10-29T23:30:00Z");
  private static final LocalDate SAST_TODAY = LocalDate.of(2026, 10, 30);

  /** A user-entered date before the boundary: whatever the server dates must land on SAST_TODAY. */
  private static final LocalDate USER_DATED = LocalDate.of(2026, 10, 28);

  @TestConfiguration
  static class FixedSastClockConfig {
    @Bean
    @Primary
    Clock fixedSastClock() {
      return Clock.fixed(BOUNDARY, ZoneId.of("Africa/Johannesburg"));
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private io.b2mash.b2b.b2bstrawman.project.ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private ClientLedgerCardRepository ledgerCardRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private CollectionsReadService collectionsReadService;
  @Autowired private EntityManager entityManager;

  private String tenantSchema;
  private UUID memberId;
  private UUID trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Posting Date Zone Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_trust_zone_owner",
                "trust_zone@test.com",
                "Trust Zone Owner",
                "owner"));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("trust_accounting"));
                  orgSettingsRepository.save(settings);
                }));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx ->
                    trustAccountId =
                        trustAccountService
                            .createTrustAccount(
                                new CreateTrustAccountRequest(
                                    "Zone Trust Account",
                                    "First National Bank",
                                    "250655",
                                    "3333333333",
                                    "GENERAL",
                                    true,
                                    false,
                                    null,
                                    LocalDate.of(2026, 1, 1),
                                    "Posting-date zone test"))
                            .id()));
  }

  @Test
  void feeTransferAndItsReversal_areDatedSastToday() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Zone Fee Client", "zone_fee@test.com", memberId));

                  // The deposit that funds the transfer is dated by the user: SAST 30 Oct.
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          trustMatterOf(customer.getId()),
                          new BigDecimal("8500.00"),
                          "DEP-ZONE-001",
                          "Funding at 01:30 SAST",
                          SAST_TODAY));

                  var invoiceId = UUID.randomUUID();
                  entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoices (id, customer_id, invoice_number, status,
                              subtotal, tax_amount, total, currency, due_date,
                              customer_name, org_name, created_by, created_at, updated_at)
                          VALUES (:id, :customerId, 'INV-ZONE-001', 'APPROVED',
                              8500.00, 0.00, 8500.00, 'ZAR', :dueDate,
                              'Zone Fee Client', 'Test Org', :createdBy, now(), now())
                          """)
                      .setParameter("id", invoiceId)
                      .setParameter("customerId", customer.getId())
                      .setParameter("dueDate", LocalDate.of(2026, 11, 30))
                      .setParameter("createdBy", memberId)
                      .executeUpdate();
                  // G89-F28: the fee transfer takes its matter from the fee note
                  entityManager
                      .createNativeQuery(
                          """
                          INSERT INTO invoice_lines (id, invoice_id, project_id, line_type,
                              description, quantity, unit_price, amount, sort_order, tax_exempt,
                              created_at, updated_at)
                          VALUES (gen_random_uuid(), :invoiceId, :projectId, 'TIME',
                              'Legal services', 1, 8500.00, 8500.00, 0, false, now(), now())
                          """)
                      .setParameter("invoiceId", invoiceId)
                      .setParameter("projectId", trustMatterOf(customer.getId()))
                      .executeUpdate();
                  entityManager.flush();

                  // The fee-transfer dialog has no date field: the server dates it.
                  var feeTransfer =
                      transactionService.recordFeeTransfer(
                          trustAccountId,
                          new RecordFeeTransferRequest(
                              customer.getId(),
                              invoiceId,
                              new BigDecimal("8500.00"),
                              "FT-ZONE-001"));

                  assertThat(feeTransfer.transactionDate()).isEqualTo(SAST_TODAY);

                  var stored = transactionRepository.findById(feeTransfer.id()).orElseThrow();
                  stored.setStatus("APPROVED");
                  transactionRepository.save(stored);

                  var reversal =
                      transactionService.reverseTransaction(feeTransfer.id(), "Zone reversal");

                  assertThat(reversal.transactionDate()).isEqualTo(SAST_TODAY);
                  assertThat(
                          ledgerCardRepository
                              .findByTrustAccountIdAndCustomerId(trustAccountId, customer.getId())
                              .orElseThrow()
                              .getLastTransactionDate())
                      .isEqualTo(SAST_TODAY);
                }));
  }

  @Test
  void transferReversal_datesBothLegsAndBothLedgersSastToday() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var source =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Zone Transfer Source", "zone_trf_src@test.com", memberId));
                  var target =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Zone Transfer Target", "zone_trf_tgt@test.com", memberId));

                  // User-dated postings sit before the boundary, so the ledgers' last-transaction
                  // date can only reach SAST_TODAY through the server-dated reversal (a UTC
                  // "today" would leave both ledgers on 29 Oct).
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          source.getId(),
                          trustMatterOf(source.getId()),
                          new BigDecimal("6000.00"),
                          "DEP-ZONE-TRF",
                          "Funding the transfer",
                          USER_DATED));
                  var legs =
                      transactionService.recordTransfer(
                          trustAccountId,
                          new RecordTransferRequest(
                              source.getId(),
                              target.getId(),
                              trustMatterOf(source.getId()),
                              new BigDecimal("2500.00"),
                              "TRF-ZONE-001",
                              "Transfer to reverse",
                              USER_DATED));
                  var transferOut = transactionRepository.findById(legs.get(0).id()).orElseThrow();
                  transferOut.setStatus("APPROVED");
                  transactionRepository.save(transferOut);

                  var outReversal =
                      transactionService.reverseTransaction(transferOut.getId(), "Zone reversal");

                  assertThat(outReversal.transactionDate()).isEqualTo(SAST_TODAY);
                  var inReversalId =
                      transactionRepository
                          .findById(legs.get(1).id())
                          .orElseThrow()
                          .getReversedById();
                  assertThat(
                          transactionRepository
                              .findById(inReversalId)
                              .orElseThrow()
                              .getTransactionDate())
                      .isEqualTo(SAST_TODAY);
                  assertThat(lastLedgerDate(source.getId())).isEqualTo(SAST_TODAY);
                  assertThat(lastLedgerDate(target.getId())).isEqualTo(SAST_TODAY);
                }));
  }

  @Test
  void refundReversal_isDatedSastToday() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Zone Refund Client", "zone_refund@test.com", memberId));
                  transactionService.recordDeposit(
                      trustAccountId,
                      new RecordDepositRequest(
                          customer.getId(),
                          trustMatterOf(customer.getId()),
                          new BigDecimal("4000.00"),
                          "DEP-ZONE-REF",
                          "Funding the refund",
                          USER_DATED));
                  var refund =
                      transactionService.recordRefund(
                          trustAccountId,
                          new RecordRefundRequest(
                              customer.getId(),
                              trustMatterOf(customer.getId()),
                              new BigDecimal("1500.00"),
                              "REF-ZONE-001",
                              "Refund to reverse",
                              USER_DATED));
                  var stored = transactionRepository.findById(refund.id()).orElseThrow();
                  stored.setStatus("APPROVED");
                  transactionRepository.save(stored);

                  var reversal =
                      transactionService.reverseTransaction(refund.id(), "Zone reversal");

                  assertThat(reversal.transactionDate()).isEqualTo(SAST_TODAY);
                  assertThat(lastLedgerDate(customer.getId())).isEqualTo(SAST_TODAY);
                }));
  }

  @Test
  void collectionsDaysOverdue_countFromSastToday() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.save(
                          TestCustomerFactory.createActiveCustomer(
                              "Zone Debtor", "zone_debtor@test.com", memberId));
                  // Due 29 Oct: "today" in UTC (not overdue) but yesterday in SA (1 day overdue).
                  insertSentFeeNote(
                      customer.getId(), "INV-ZONE-DUE-29", LocalDate.of(2026, 10, 29));
                  // Due 30 Oct: "tomorrow" in UTC but today in SA (due, not yet overdue).
                  insertSentFeeNote(customer.getId(), "INV-ZONE-DUE-30", SAST_TODAY);

                  var daysOverdueByNumber =
                      collectionsReadService
                          .getDebtor(customer.getId(), PageRequest.of(0, 10))
                          .outstandingInvoices()
                          .stream()
                          .collect(
                              Collectors.toMap(
                                  OutstandingInvoice::invoiceNumber,
                                  OutstandingInvoice::daysOverdue));

                  // A UTC "today" gives {0, -1}; the DB CURRENT_DATE gives the real wall date.
                  assertThat(daysOverdueByNumber)
                      .containsExactlyInAnyOrderEntriesOf(
                          Map.of("INV-ZONE-DUE-29", 1, "INV-ZONE-DUE-30", 0));
                }));
  }

  @Test
  void trustAccountOpenedAndClosed_defaultToSastToday() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var opened =
                      trustAccountService.createTrustAccount(
                          new CreateTrustAccountRequest(
                              "Zone Account To Close",
                              "First National Bank",
                              "250655",
                              "4444444444",
                              "GENERAL",
                              false,
                              false,
                              null,
                              null,
                              "Opened and closed by the server clock"));

                  assertThat(opened.openedDate()).isEqualTo(SAST_TODAY);

                  var closed = trustAccountService.closeTrustAccount(opened.id());

                  assertThat(closed.closedDate()).isEqualTo(SAST_TODAY);
                }));
  }

  private LocalDate lastLedgerDate(UUID customerId) {
    return ledgerCardRepository
        .findByTrustAccountIdAndCustomerId(trustAccountId, customerId)
        .orElseThrow()
        .getLastTransactionDate();
  }

  private void insertSentFeeNote(UUID customerId, String invoiceNumber, LocalDate dueDate) {
    entityManager
        .createNativeQuery(
            """
            INSERT INTO invoices (id, customer_id, invoice_number, status,
                subtotal, tax_amount, total, currency, due_date,
                customer_name, org_name, created_by, created_at, updated_at)
            VALUES (:id, :customerId, :invoiceNumber, 'SENT',
                1000.00, 0.00, 1000.00, 'ZAR', :dueDate,
                'Zone Debtor', 'Test Org', :createdBy, now(), now())
            """)
        .setParameter("id", UUID.randomUUID())
        .setParameter("customerId", customerId)
        .setParameter("invoiceNumber", invoiceNumber)
        .setParameter("dueDate", dueDate)
        .setParameter("createdBy", memberId)
        .executeUpdate();
    entityManager.flush();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.of("APPROVE_TRUST_PAYMENT", "MANAGE_TRUST", "VIEW_TRUST"))
        .run(action);
  }

  private final java.util.Map<UUID, UUID> trustMatters = new java.util.HashMap<>();

  /** G89-F28: a client's trust postings sit on a matter of its own (one per client). */
  private UUID trustMatterOf(UUID clientId) {
    return trustMatters.computeIfAbsent(
        clientId,
        id ->
            TestTrustMatterHelper.openClientMatter(
                tenantSchema,
                transactionTemplate,
                projectRepository,
                id,
                "Trust matter " + id,
                memberId));
  }
}

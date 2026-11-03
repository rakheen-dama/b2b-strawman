package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordFeeTransferRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G88-F48: server-dated trust postings (fee transfer, reversal, account open/close) must be dated
 * in South African time. The clock is pinned at 2026-10-29T23:30Z, which is 01:30 SAST on
 * 2026-10-30 — a UTC "today" would date these postings a day early, before the deposit that funds
 * them.
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

  @TestConfiguration
  static class FixedSastClockConfig {
    @Bean
    @Primary
    Clock fixedSastClock() {
      return Clock.fixed(BOUNDARY, ZoneId.of("Africa/Johannesburg"));
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private ClientLedgerCardRepository ledgerCardRepository;
  @Autowired private CustomerRepository customerRepository;
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
                          null,
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
}

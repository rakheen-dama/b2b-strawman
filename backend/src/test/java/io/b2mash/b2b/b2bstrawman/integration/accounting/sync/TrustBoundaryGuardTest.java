package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnection;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnectionRepository;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.MemberSyncService;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustBoundaryGuardTest {

  private static final String ORG_ID = "org_trust_boundary_test";

  @Autowired private TrustBoundaryGuard trustBoundaryGuard;
  @Autowired private AccountingSyncService syncService;
  @Autowired private AccountingSyncEntryRepository syncEntryRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private ClientLedgerCardRepository clientLedgerCardRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TrustTransactionRepository trustTransactionRepository;
  @Autowired private AccountingXeroConnectionRepository xeroConnectionRepository;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private MemberSyncService memberSyncService;

  private String tenantSchema;
  private UUID memberId;

  @BeforeAll
  void setUp() {
    provisioningService.provisionTenant(ORG_ID, "Trust Boundary Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create a real member to satisfy the customers.created_by FK
    var syncResult =
        memberSyncService.syncMember(
            ORG_ID,
            "user_trust_guard_test",
            "trust-guard@test.com",
            "Trust Guard Tester",
            null,
            "owner");
    memberId = syncResult.memberId();

    runInTenant(this::createConnectedXeroConnection);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }

  private void createConnectedXeroConnection() {
    var orgIntegration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
    orgIntegration.enable();
    var savedIntegration = orgIntegrationRepository.save(orgIntegration);

    var connection =
        new AccountingXeroConnection(
            savedIntegration.getId(),
            "xero-tenant-" + UUID.randomUUID().toString().substring(0, 8),
            "Test Xero Org",
            UUID.randomUUID(),
            Instant.now().plus(30, ChronoUnit.MINUTES),
            "accounting.transactions openid profile email");
    xeroConnectionRepository.save(connection);
  }

  private Customer createAndSaveCustomer(String name) {
    var customer = TestCustomerFactory.createActiveCustomer(name, name + "@test.com", memberId);
    return customerRepository.save(customer);
  }

  private Invoice createAndSaveInvoice(UUID customerId, String customerName) {
    // Use USD to match the default org currency (provisioned with null country -> USD)
    var invoice = new Invoice(customerId, "USD", customerName, null, null, "Test Org", memberId);
    return invoiceRepository.save(invoice);
  }

  private InvoiceLine createAndSaveInvoiceLine(UUID invoiceId, String description) {
    var line =
        new InvoiceLine(invoiceId, null, null, description, BigDecimal.ONE, BigDecimal.TEN, 1);
    return invoiceLineRepository.save(line);
  }

  @Test
  void evaluate_refusesInvoiceWithTrustFlag() {
    runInTenant(
        () -> {
          var customer = createAndSaveCustomer("Trust Flag Customer");
          var invoice = createAndSaveInvoice(customer.getId(), customer.getName());
          invoice.setCustomFields(Map.of("is_trust_invoice", true));
          invoiceRepository.save(invoice);

          var line = createAndSaveInvoiceLine(invoice.getId(), "Legal service");

          var decision = trustBoundaryGuard.evaluate(invoice, List.of(line), customer);

          assertThat(decision.allowed()).isFalse();
          assertThat(decision.reason()).contains("is_trust_invoice=true");
        });
  }

  @Test
  void evaluate_refusesInvoiceLineLinkedToTrustDisbursement() {
    runInTenant(
        () -> {
          var customer = createAndSaveCustomer("Disbursement Customer");
          var invoice = createAndSaveInvoice(customer.getId(), customer.getName());

          // Create a real project for the disbursement FK
          var project = new Project("Trust Disbursement Matter", null, memberId);
          var savedProject = projectRepository.save(project);

          // Create a trust account and trust transaction for the disbursement FK
          var trustAccount =
              new TrustAccount(
                  "General Trust",
                  "Test Bank",
                  "001",
                  "1234567890",
                  TrustAccountType.GENERAL,
                  true,
                  false,
                  null,
                  LocalDate.now(),
                  null);
          var savedTrustAccount = trustAccountRepository.save(trustAccount);

          var trustTx =
              new TrustTransaction(
                  savedTrustAccount.getId(),
                  "PAYMENT",
                  BigDecimal.valueOf(500),
                  customer.getId(),
                  savedProject.getId(),
                  null,
                  "TRUST-DISB-001",
                  "Trust disbursement for court fee",
                  LocalDate.now(),
                  "APPROVED",
                  memberId);
          var savedTrustTx = trustTransactionRepository.save(trustTx);

          // Create a trust-linked disbursement
          var disbursement =
              new LegalDisbursement(
                  savedProject.getId(),
                  customer.getId(),
                  "COURT_FEES",
                  "Trust-funded court fee",
                  BigDecimal.valueOf(500),
                  "EXEMPT",
                  BigDecimal.ZERO,
                  DisbursementPaymentSource.TRUST_ACCOUNT.name(),
                  savedTrustTx.getId(),
                  LocalDate.now(),
                  "Court",
                  null,
                  null,
                  memberId);
          var savedDisbursement = disbursementRepository.save(disbursement);

          // Create an invoice line linked to the disbursement
          var line =
              new InvoiceLine(
                  invoice.getId(),
                  savedProject.getId(),
                  null,
                  "Court fee (trust)",
                  BigDecimal.ONE,
                  BigDecimal.valueOf(500),
                  1);
          line.setDisbursementId(savedDisbursement.getId());
          invoiceLineRepository.save(line);

          var decision = trustBoundaryGuard.evaluate(invoice, List.of(line), customer);

          assertThat(decision.allowed()).isFalse();
          assertThat(decision.reason()).contains("sourced from trust account");
        });
  }

  @Test
  void evaluate_refusesCustomerWithNonZeroTrustBalance() {
    runInTenant(
        () -> {
          var customer = createAndSaveCustomer("Trust Balance Customer");
          var invoice = createAndSaveInvoice(customer.getId(), customer.getName());
          var line = createAndSaveInvoiceLine(invoice.getId(), "Legal service");

          // Create a trust account (non-primary to avoid unique constraint conflicts)
          var trustAccount =
              new TrustAccount(
                  "Balance Check Trust",
                  "Test Bank",
                  "002",
                  "9876543210",
                  TrustAccountType.INVESTMENT,
                  false,
                  false,
                  null,
                  LocalDate.now(),
                  null);
          var savedTrustAccount = trustAccountRepository.save(trustAccount);

          var ledgerCard = new ClientLedgerCard(savedTrustAccount.getId(), customer.getId());
          ledgerCard.addDeposit(BigDecimal.valueOf(1000), LocalDate.now());
          clientLedgerCardRepository.save(ledgerCard);

          var decision = trustBoundaryGuard.evaluate(invoice, List.of(line), customer);

          assertThat(decision.allowed()).isFalse();
          assertThat(decision.reason()).contains("active trust balance");
          assertThat(decision.reason()).contains(customer.getName());
        });
  }

  @Test
  void evaluate_permitsNonTrustInvoice() {
    runInTenant(
        () -> {
          var customer = createAndSaveCustomer("Regular Customer");
          var invoice = createAndSaveInvoice(customer.getId(), customer.getName());
          var line = createAndSaveInvoiceLine(invoice.getId(), "Standard service");

          var decision = trustBoundaryGuard.evaluate(invoice, List.of(line), customer);

          assertThat(decision.allowed()).isTrue();
          assertThat(decision.reason()).isNull();
        });
  }

  @Test
  void evaluate_failsClosed_onException() {
    runInTenant(
        () -> {
          var customer = createAndSaveCustomer("Error Customer");
          var invoice = createAndSaveInvoice(customer.getId(), customer.getName());

          // Pass null lines to trigger NullPointerException in the for-each loop,
          // which the fail-closed handler should catch and return refused.
          var decision = trustBoundaryGuard.evaluate(invoice, null, customer);

          assertThat(decision.allowed()).isFalse();
          assertThat(decision.reason()).contains("Guard evaluation failed");
        });
  }

  @Test
  void evaluate_permitsInvoiceWithOfficeDisbursement() {
    runInTenant(
        () -> {
          var customer = createAndSaveCustomer("Office Disbursement Customer");
          var invoice = createAndSaveInvoice(customer.getId(), customer.getName());

          // Create a real project for the disbursement FK
          var project = new Project("Office Disbursement Matter", null, memberId);
          var savedProject = projectRepository.save(project);

          // Create an office-account disbursement (NOT trust)
          var disbursement =
              new LegalDisbursement(
                  savedProject.getId(),
                  customer.getId(),
                  "COURT_FEES",
                  "Office-funded court fee",
                  BigDecimal.valueOf(200),
                  "EXEMPT",
                  BigDecimal.ZERO,
                  DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                  null, // no trustTransactionId
                  LocalDate.now(),
                  "Court",
                  null,
                  null,
                  memberId);
          var savedDisbursement = disbursementRepository.save(disbursement);

          var line =
              new InvoiceLine(
                  invoice.getId(),
                  savedProject.getId(),
                  null,
                  "Court fee (office)",
                  BigDecimal.ONE,
                  BigDecimal.valueOf(200),
                  1);
          line.setDisbursementId(savedDisbursement.getId());
          invoiceLineRepository.save(line);

          var decision = trustBoundaryGuard.evaluate(invoice, List.of(line), customer);

          assertThat(decision.allowed()).isTrue();
        });
  }

  @Test
  void enqueueInvoicePush_createsBlockedEntryWhenGuardRefuses() {
    runInTenant(
        () -> {
          var customer = createAndSaveCustomer("Blocked Enqueue Customer");
          var invoice = createAndSaveInvoice(customer.getId(), customer.getName());
          invoice.setCustomFields(Map.of("is_trust_invoice", true));
          invoiceRepository.save(invoice);
          createAndSaveInvoiceLine(invoice.getId(), "Trust service");

          syncService.enqueueInvoicePush(invoice.getId(), SyncTrigger.EVENT);

          var entries = syncEntryRepository.findByEntity(SyncEntityType.INVOICE, invoice.getId());
          assertThat(entries).hasSize(1);

          var entry = entries.getFirst();
          assertThat(entry.getState()).isEqualTo(SyncState.BLOCKED_TRUST_BOUNDARY);
          assertThat(entry.getLastErrorCode()).isEqualTo("TRUST_BOUNDARY");
          assertThat(entry.getLastErrorDetail()).contains("is_trust_invoice=true");
        });
  }
}

package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.ApprovalDecisionRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.CreateDisbursementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.UpdateDisbursementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.WriteOffRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementApprovedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementBilledEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementRejectedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service-level integration test for {@link DisbursementService}. Covers the approval lifecycle
 * (DRAFT → PENDING → APPROVED/REJECTED), VAT defaulting per category, write-off rules, billing,
 * statement-range query, and trust-link validation (both OFFICE and TRUST branches).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({
  TestcontainersConfiguration.class,
  DisbursementServiceIntegrationTest.TestDisbursementEventCapture.class
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisbursementServiceIntegrationTest {
  private static final String ORG_ID = "org_disb_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private DisbursementService disbursementService;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TrustTransactionRepository trustTransactionRepository;
  @Autowired private TestDisbursementEventCapture eventCapture;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;
  private UUID otherProjectId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Disbursement Service Test Org", "legal-za")
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_disb_svc_owner",
                "disb_svc_owner@test.com",
                "Disb Svc Owner",
                "owner"));

    // Enable disbursements + trust_accounting modules
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("disbursements", "trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    // Create customer + two projects under the tenant
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer(
                              "Disb Svc Test Client", "disb_svc_client@test.com", memberId));
                  customerId = customer.getId();

                  var project = new Project("Disb Test Matter", "Test matter", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();

                  var other = new Project("Other Matter", "Other", memberId);
                  other.setCustomerId(customerId);
                  other = projectRepository.saveAndFlush(other);
                  otherProjectId = other.getId();
                }));
  }

  // --- 486.14: Lifecycle + transitions ---

  @Test
  void create_yieldsDraftAndUnbilledWithVatDefaultedPerCategory() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var req = officeReq("Sheriff fee", DisbursementCategory.SHERIFF_FEES, null);

                  var resp = disbursementService.create(req);

                  assertThat(resp.approvalStatus()).isEqualTo("DRAFT");
                  assertThat(resp.billingStatus()).isEqualTo("UNBILLED");
                  assertThat(resp.vatTreatment()).isEqualTo(VatTreatment.ZERO_RATED_PASS_THROUGH);
                  assertThat(resp.vatAmount()).isEqualByComparingTo(BigDecimal.ZERO);
                  assertThat(resp.currency()).isEqualTo("ZAR");
                }));
  }

  @Test
  void create_withExplicitVatTreatment_overridesDefault() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var req =
                      officeReq(
                          "Sheriff with explicit VAT",
                          DisbursementCategory.SHERIFF_FEES,
                          VatTreatment.STANDARD_15);

                  var resp = disbursementService.create(req);

                  assertThat(resp.vatTreatment()).isEqualTo(VatTreatment.STANDARD_15);
                  assertThat(resp.vatAmount())
                      .isEqualByComparingTo(req.amount().multiply(new BigDecimal("0.15")));
                }));
  }

  @Test
  void submitForApproval_transitionsDraftToPending_thenBlocksFromApproved() {
    // Setup: create + submit + approve inside an isolated TX so the outer block can run a
    // throwing assertion without marking the parent TX rollback-only.
    var idRef = new java.util.concurrent.atomic.AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var created =
                      disbursementService.create(
                          officeReq("Submit flow", DisbursementCategory.COUNSEL_FEES, null));
                  var pending = disbursementService.submitForApproval(created.id());
                  assertThat(pending.approvalStatus()).isEqualTo("PENDING_APPROVAL");
                  disbursementService.approve(created.id(), new ApprovalDecisionRequest("ok"));
                  idRef.set(created.id());
                }));

    runInTenant(
        () ->
            assertThatThrownBy(() -> disbursementService.submitForApproval(idRef.get()))
                .isInstanceOf(InvalidStateException.class));
  }

  @Test
  void approve_firesEventAndSetsApproverAndTimestamp() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  eventCapture.reset();
                  var created =
                      disbursementService.create(
                          officeReq("Approve flow", DisbursementCategory.COUNSEL_FEES, null));
                  disbursementService.submitForApproval(created.id());
                  var approved =
                      disbursementService.approve(
                          created.id(), new ApprovalDecisionRequest("looks good"));

                  assertThat(approved.approvalStatus()).isEqualTo("APPROVED");
                  assertThat(approved.approvedBy()).isEqualTo(memberId);
                  assertThat(approved.approvedAt()).isNotNull();
                  assertThat(approved.approvalNotes()).isEqualTo("looks good");

                  assertThat(eventCapture.lastApproved.get()).isNotNull();
                  assertThat(eventCapture.lastApproved.get().disbursementId())
                      .isEqualTo(created.id());
                }));
  }

  @Test
  void reject_firesEventAndSetsRejectedState() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  eventCapture.reset();
                  var created =
                      disbursementService.create(
                          officeReq("Reject flow", DisbursementCategory.COUNSEL_FEES, null));
                  disbursementService.submitForApproval(created.id());
                  var rejected =
                      disbursementService.reject(
                          created.id(), new ApprovalDecisionRequest("needs receipt"));

                  assertThat(rejected.approvalStatus()).isEqualTo("REJECTED");
                  assertThat(rejected.approvalNotes()).isEqualTo("needs receipt");
                  assertThat(eventCapture.lastRejected.get()).isNotNull();
                  assertThat(eventCapture.lastRejected.get().disbursementId())
                      .isEqualTo(created.id());
                }));
  }

  @Test
  void update_onApprovedDisbursement_throws() {
    var idRef = new java.util.concurrent.atomic.AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var created =
                      disbursementService.create(
                          officeReq("Update blocked", DisbursementCategory.COUNSEL_FEES, null));
                  disbursementService.submitForApproval(created.id());
                  disbursementService.approve(created.id(), new ApprovalDecisionRequest("ok"));
                  idRef.set(created.id());
                }));

    var updateReq =
        new UpdateDisbursementRequest(
            null, null, "new description", null, null, null, null, null, null);
    runInTenant(
        () ->
            assertThatThrownBy(() -> disbursementService.update(idRef.get(), updateReq))
                .isInstanceOfAny(InvalidStateException.class, IllegalStateException.class));
  }

  @Test
  void writeOff_unbilledWithReason_succeeds_andBlocksWithoutReason() {
    var id2Ref = new java.util.concurrent.atomic.AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var created =
                      disbursementService.create(
                          officeReq("Write off flow", DisbursementCategory.COUNSEL_FEES, null));

                  var written =
                      disbursementService.writeOff(
                          created.id(), new WriteOffRequest("client declined"));
                  assertThat(written.billingStatus()).isEqualTo("WRITTEN_OFF");
                  assertThat(written.writeOffReason()).isEqualTo("client declined");

                  // Second disbursement for the missing-reason rejection assertion (asserted
                  // outside
                  // this TX so the rollback-only flag does not leak back up).
                  var created2 =
                      disbursementService.create(
                          officeReq(
                              "Write off no reason", DisbursementCategory.COUNSEL_FEES, null));
                  id2Ref.set(created2.id());
                }));

    runInTenant(
        () ->
            assertThatThrownBy(
                    () -> disbursementService.writeOff(id2Ref.get(), new WriteOffRequest("")))
                .isInstanceOf(Exception.class));
  }

  @Test
  void writeOff_onBilled_throws() {
    var idRef = new java.util.concurrent.atomic.AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var created =
                      disbursementService.create(
                          officeReq("Bill then writeoff", DisbursementCategory.COUNSEL_FEES, null));
                  disbursementService.submitForApproval(created.id());
                  disbursementService.approve(created.id(), new ApprovalDecisionRequest("ok"));
                  disbursementService.markBilled(created.id(), UUID.randomUUID());
                  idRef.set(created.id());
                }));

    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        disbursementService.writeOff(idRef.get(), new WriteOffRequest("too late")))
                .isInstanceOf(InvalidStateException.class));
  }

  @Test
  void markBilled_firesEventWithInvoiceLineId() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  eventCapture.reset();
                  var created =
                      disbursementService.create(
                          officeReq("Bill me", DisbursementCategory.COUNSEL_FEES, null));
                  disbursementService.submitForApproval(created.id());
                  disbursementService.approve(created.id(), new ApprovalDecisionRequest("ok"));

                  var invoiceLineId = UUID.randomUUID();
                  var billed = disbursementService.markBilled(created.id(), invoiceLineId);

                  assertThat(billed.billingStatus()).isEqualTo("BILLED");
                  assertThat(billed.billedInvoiceLineId()).isEqualTo(invoiceLineId);
                  assertThat(eventCapture.lastBilled.get()).isNotNull();
                  assertThat(eventCapture.lastBilled.get().invoiceLineId())
                      .isEqualTo(invoiceLineId);
                }));
  }

  @Test
  void listForStatement_respectsDateRangeAndOrdersByCategoryThenDate() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Seed: two approved entries inside the range + one outside
                  var a =
                      disbursementService.create(
                          officeReqWithDate(
                              "Sheriff A",
                              DisbursementCategory.SHERIFF_FEES,
                              null,
                              LocalDate.of(2026, 2, 10)));
                  disbursementService.submitForApproval(a.id());
                  disbursementService.approve(a.id(), new ApprovalDecisionRequest("ok"));

                  var b =
                      disbursementService.create(
                          officeReqWithDate(
                              "Counsel B",
                              DisbursementCategory.COUNSEL_FEES,
                              null,
                              LocalDate.of(2026, 2, 20)));
                  disbursementService.submitForApproval(b.id());
                  disbursementService.approve(b.id(), new ApprovalDecisionRequest("ok"));

                  var c =
                      disbursementService.create(
                          officeReqWithDate(
                              "Outside range",
                              DisbursementCategory.COUNSEL_FEES,
                              null,
                              LocalDate.of(2026, 1, 1)));
                  disbursementService.submitForApproval(c.id());
                  disbursementService.approve(c.id(), new ApprovalDecisionRequest("ok"));

                  var rows =
                      disbursementService.listForStatement(
                          projectId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

                  assertThat(rows)
                      .extracting(r -> r.category())
                      .contains(
                          DisbursementCategory.SHERIFF_FEES, DisbursementCategory.COUNSEL_FEES);
                  assertThat(rows).extracting(r -> r.id()).doesNotContain(c.id());
                  // First row alphabetical by category: COUNSEL_FEES before SHERIFF_FEES
                  assertThat(rows.get(0).category()).isEqualTo(DisbursementCategory.COUNSEL_FEES);
                }));
  }

  // --- 486.15: Trust-link validation (both branches) ---

  @Test
  void create_officeAccountWithNullTrustTxnId_succeeds() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var resp =
                      disbursementService.create(
                          officeReq("Office OK", DisbursementCategory.COUNSEL_FEES, null));
                  assertThat(resp.id()).isNotNull();
                  assertThat(resp.paymentSource())
                      .isEqualTo(DisbursementPaymentSource.OFFICE_ACCOUNT);
                }));
  }

  @Test
  void create_officeAccountWithNonNullTrustTxnId_failsAtServiceValidation() {
    var req =
        new CreateDisbursementRequest(
            projectId,
            customerId,
            "Office with rogue trust id",
            new BigDecimal("100.00"),
            "ZAR",
            DisbursementCategory.COUNSEL_FEES,
            null,
            DisbursementPaymentSource.OFFICE_ACCOUNT,
            UUID.randomUUID(),
            LocalDate.of(2026, 3, 1),
            null);
    runInTenant(
        () ->
            assertThatThrownBy(() -> disbursementService.create(req))
                .isInstanceOf(InvalidStateException.class));
  }

  @Test
  void create_trustAccountWithApprovedDisbursementTxnForSameMatter_succeeds() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var trustTxnId = seedApprovedTrustTxn(projectId);
                  var req =
                      new CreateDisbursementRequest(
                          projectId,
                          customerId,
                          "Trust-linked disbursement",
                          new BigDecimal("500.00"),
                          "ZAR",
                          DisbursementCategory.COUNSEL_FEES,
                          null,
                          DisbursementPaymentSource.TRUST_ACCOUNT,
                          trustTxnId,
                          LocalDate.of(2026, 3, 1),
                          null);

                  var resp = disbursementService.create(req);

                  assertThat(resp.trustTransactionId()).isEqualTo(trustTxnId);
                  assertThat(resp.paymentSource())
                      .isEqualTo(DisbursementPaymentSource.TRUST_ACCOUNT);
                }));
  }

  @Test
  void create_trustAccountWithTxnForDifferentProject_fails() {
    var trustTxnIdRef = new java.util.concurrent.atomic.AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> trustTxnIdRef.set(seedApprovedTrustTxn(otherProjectId))));
    var req =
        new CreateDisbursementRequest(
            projectId,
            customerId,
            "Mismatched trust project",
            new BigDecimal("500.00"),
            "ZAR",
            DisbursementCategory.COUNSEL_FEES,
            null,
            DisbursementPaymentSource.TRUST_ACCOUNT,
            trustTxnIdRef.get(),
            LocalDate.of(2026, 3, 1),
            null);
    runInTenant(
        () ->
            assertThatThrownBy(() -> disbursementService.create(req))
                .isInstanceOf(InvalidStateException.class));
  }

  @Test
  void create_trustAccountWithUnapprovedTxn_fails() {
    var trustTxnIdRef = new java.util.concurrent.atomic.AtomicReference<UUID>();
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> trustTxnIdRef.set(seedUnapprovedTrustTxn(projectId))));
    var req =
        new CreateDisbursementRequest(
            projectId,
            customerId,
            "Unapproved trust txn",
            new BigDecimal("500.00"),
            "ZAR",
            DisbursementCategory.COUNSEL_FEES,
            null,
            DisbursementPaymentSource.TRUST_ACCOUNT,
            trustTxnIdRef.get(),
            LocalDate.of(2026, 3, 1),
            null);
    runInTenant(
        () ->
            assertThatThrownBy(() -> disbursementService.create(req))
                .isInstanceOf(InvalidStateException.class));
  }

  // --- Helpers ---

  private CreateDisbursementRequest officeReq(
      String description, DisbursementCategory category, VatTreatment vatOverride) {
    return officeReqWithDate(description, category, vatOverride, LocalDate.of(2026, 3, 1));
  }

  private CreateDisbursementRequest officeReqWithDate(
      String description,
      DisbursementCategory category,
      VatTreatment vatOverride,
      LocalDate incurredDate) {
    return new CreateDisbursementRequest(
        projectId,
        customerId,
        description,
        new BigDecimal("100.00"),
        "ZAR",
        category,
        vatOverride,
        DisbursementPaymentSource.OFFICE_ACCOUNT,
        null,
        incurredDate,
        "Supplier X");
  }

  /** Seeds an APPROVED DISBURSEMENT_PAYMENT trust transaction targeting the given project. */
  private UUID seedApprovedTrustTxn(UUID linkedProjectId) {
    // Need a trust_account_id — any UUID works since there's no FK (depends on V85 DDL, but
    // trust_transactions.trust_account_id is a REFERENCES column. Create a trust_account row via
    // JdbcTemplate so the FK holds.)
    var trustAccountId = UUID.randomUUID();
    insertTrustAccount(trustAccountId);

    var txn =
        new TrustTransaction(
            trustAccountId,
            "DISBURSEMENT_PAYMENT",
            new BigDecimal("500.00"),
            customerId,
            linkedProjectId,
            null,
            "DISB-" + UUID.randomUUID().toString().substring(0, 8),
            "seeded disbursement payment",
            LocalDate.of(2026, 2, 15),
            "APPROVED",
            memberId);
    return trustTransactionRepository.saveAndFlush(txn).getId();
  }

  /** Seeds a PENDING DISBURSEMENT_PAYMENT trust transaction. */
  private UUID seedUnapprovedTrustTxn(UUID linkedProjectId) {
    var trustAccountId = UUID.randomUUID();
    insertTrustAccount(trustAccountId);

    var txn =
        new TrustTransaction(
            trustAccountId,
            "DISBURSEMENT_PAYMENT",
            new BigDecimal("500.00"),
            customerId,
            linkedProjectId,
            null,
            "DISB-P-" + UUID.randomUUID().toString().substring(0, 8),
            "seeded pending payment",
            LocalDate.of(2026, 2, 15),
            "AWAITING_APPROVAL",
            memberId);
    return trustTransactionRepository.saveAndFlush(txn).getId();
  }

  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  private void insertTrustAccount(UUID id) {
    jdbcTemplate.update(
        "INSERT INTO "
            + tenantSchema
            + ".trust_accounts (id, account_name, bank_name, branch_code, account_number,"
            + " account_type, is_primary, require_dual_approval, status, opened_date, created_at,"
            + " updated_at) VALUES (?, ?, 'FNB', '250655', ?, 'GENERAL', false, false, 'ACTIVE',"
            + " ?, now(), now())",
        id,
        "Trust " + id.toString().substring(0, 8),
        UUID.randomUUID().toString().substring(0, 10),
        LocalDate.of(2026, 1, 1));
  }

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  /** Event capture bean scoped to the test context. */
  @Component
  static class TestDisbursementEventCapture {
    final AtomicReference<DisbursementApprovedEvent> lastApproved = new AtomicReference<>();
    final AtomicReference<DisbursementRejectedEvent> lastRejected = new AtomicReference<>();
    final AtomicReference<DisbursementBilledEvent> lastBilled = new AtomicReference<>();

    @EventListener
    public void onApproved(DisbursementApprovedEvent e) {
      lastApproved.set(e);
    }

    @EventListener
    public void onRejected(DisbursementRejectedEvent e) {
      lastRejected.set(e);
    }

    @EventListener
    public void onBilled(DisbursementBilledEvent e) {
      lastBilled.set(e);
    }

    void reset() {
      lastApproved.set(null);
      lastRejected.set(null);
      lastBilled.set(null);
    }
  }
}

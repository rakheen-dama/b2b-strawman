package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.CreateDisbursementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.UpdateDisbursementRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementApprovedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementBilledEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.event.DisbursementRejectedEvent;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountType;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link DisbursementService} (Epic 486B tasks 486.14 + 486.15). Covers VAT
 * defaults, state transitions, event publication, trust-link validation, and the
 * listForStatement/listUnbilled read-side projections.
 */
// Keeps @AutoConfigureMockMvc because TestMemberHelper.syncMember requires MockMvc to drive the
// internal member-sync endpoint during @BeforeAll setup. No HTTP assertions live in this class —
// it is a service-level test despite the MockMvc dependency.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisbursementServiceTest {

  private static final String ORG_ID = "org_disbursement_svc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private DisbursementService disbursementService;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TrustTransactionRepository trustTransactionRepository;
  @Autowired private TrustAccountRepository trustAccountRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private ApplicationEvents events;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;
  private UUID otherProjectId;
  private UUID trustAccountId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Disbursement Service Test", null);

    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_disb_svc_owner",
                "disb_svc_owner@test.com",
                "Disbursement Svc Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Enable the "disbursements" vertical module on this tenant.
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("disbursements"));
                  orgSettingsRepository.save(settings);

                  var customer =
                      createActiveCustomer("Disbursement Svc Client", "dsvc@test.com", memberId);
                  customer = customerRepository.saveAndFlush(customer);
                  customerId = customer.getId();

                  var project =
                      new Project(
                          "Disbursement Matter Svc", "Matter for disbursement svc tests", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();

                  var otherProject =
                      new Project(
                          "Other Matter Svc", "Different matter for trust link checks", memberId);
                  otherProject.setCustomerId(customerId);
                  otherProject = projectRepository.saveAndFlush(otherProject);
                  otherProjectId = otherProject.getId();

                  // Seed a real TrustAccount so trust_transactions FK resolves.
                  var trustAccount =
                      new TrustAccount(
                          "Disbursement Svc Trust",
                          "Test Bank",
                          "250655",
                          "6200-000-SVC",
                          TrustAccountType.GENERAL,
                          true,
                          false,
                          null,
                          LocalDate.of(2026, 1, 1),
                          "Seed trust account for disbursement service tests");
                  trustAccount = trustAccountRepository.saveAndFlush(trustAccount);
                  trustAccountId = trustAccount.getId();
                }));
  }

  // ==========================================================================
  // 486.14 — VAT defaults, explicit overrides, state transitions, events,
  //           listForStatement
  // ==========================================================================

  @Test
  void create_withoutVatTreatment_appliesCategoryDefault_SHERIFF_FEES() {
    runInTenant(
        () -> {
          var response =
              disbursementService.create(
                  officeRequest(DisbursementCategory.SHERIFF_FEES, null), memberId);
          assertThat(response.vatTreatment()).isEqualTo(VatTreatment.ZERO_RATED_PASS_THROUGH);
          assertThat(response.vatAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        });
  }

  @Test
  void create_withoutVatTreatment_appliesCategoryDefault_COUNSEL_FEES() {
    runInTenant(
        () -> {
          var req = officeRequest(DisbursementCategory.COUNSEL_FEES, null);
          var response = disbursementService.create(req, memberId);
          assertThat(response.vatTreatment()).isEqualTo(VatTreatment.STANDARD_15);
          // 15% of 250.00 = 37.50
          assertThat(response.vatAmount()).isEqualByComparingTo(new BigDecimal("37.50"));
        });
  }

  @Test
  void create_withExplicitVatTreatment_persistsOverride() {
    runInTenant(
        () -> {
          var req = officeRequest(DisbursementCategory.COUNSEL_FEES, VatTreatment.EXEMPT);
          var response = disbursementService.create(req, memberId);
          assertThat(response.vatTreatment()).isEqualTo(VatTreatment.EXEMPT);
          assertThat(response.vatAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        });
  }

  @Test
  void submitForApproval_DRAFT_transitionsToPendingApproval() {
    runInTenant(
        () -> {
          var created =
              disbursementService.create(officeRequest(DisbursementCategory.OTHER, null), memberId);
          var updated = disbursementService.submitForApproval(created.id());
          assertThat(updated.approvalStatus())
              .isEqualTo(DisbursementApprovalStatus.PENDING_APPROVAL);
        });
  }

  @Test
  void submitForApproval_APPROVED_throwsInvalidStateException() {
    runInTenant(
        () -> {
          var created =
              disbursementService.create(officeRequest(DisbursementCategory.OTHER, null), memberId);
          disbursementService.submitForApproval(created.id());
          disbursementService.approve(created.id(), memberId, "ok");

          assertThatThrownBy(() -> disbursementService.submitForApproval(created.id()))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void approve_PENDING_setsApproverAndTimestamp_andPublishesEvent() {
    runInTenant(
        () -> {
          var created =
              disbursementService.create(officeRequest(DisbursementCategory.OTHER, null), memberId);
          disbursementService.submitForApproval(created.id());

          var approved = disbursementService.approve(created.id(), memberId, "looks good");
          assertThat(approved.approvalStatus()).isEqualTo(DisbursementApprovalStatus.APPROVED);
          assertThat(approved.approvedBy()).isEqualTo(memberId);
          assertThat(approved.approvedAt()).isNotNull();
          assertThat(approved.approvalNotes()).isEqualTo("looks good");

          long count =
              events.stream(DisbursementApprovedEvent.class)
                  .filter(e -> e.disbursementId().equals(created.id()))
                  .count();
          assertThat(count).isEqualTo(1);
        });
  }

  @Test
  void reject_PENDING_publishesRejectedEvent() {
    runInTenant(
        () -> {
          var created =
              disbursementService.create(officeRequest(DisbursementCategory.OTHER, null), memberId);
          disbursementService.submitForApproval(created.id());

          var rejected = disbursementService.reject(created.id(), memberId, "missing receipt");
          assertThat(rejected.approvalStatus()).isEqualTo(DisbursementApprovalStatus.REJECTED);
          assertThat(rejected.approvalNotes()).isEqualTo("missing receipt");

          long count =
              events.stream(DisbursementRejectedEvent.class)
                  .filter(e -> e.disbursementId().equals(created.id()))
                  .count();
          assertThat(count).isEqualTo(1);
        });
  }

  @Test
  void update_APPROVED_throwsInvalidStateException() {
    runInTenant(
        () -> {
          var created =
              disbursementService.create(officeRequest(DisbursementCategory.OTHER, null), memberId);
          disbursementService.submitForApproval(created.id());
          disbursementService.approve(created.id(), memberId, null);

          var updateReq =
              new UpdateDisbursementRequest(
                  null, "new description", null, null, null, null, null, null);
          assertThatThrownBy(() -> disbursementService.update(created.id(), updateReq))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void writeOff_UNBILLED_transitionsAndPersistsReason() {
    runInTenant(
        () -> {
          var created =
              disbursementService.create(officeRequest(DisbursementCategory.OTHER, null), memberId);
          var result = disbursementService.writeOff(created.id(), "client insolvent");
          assertThat(result.billingStatus()).isEqualTo(DisbursementBillingStatus.WRITTEN_OFF);
          assertThat(result.writeOffReason()).isEqualTo("client insolvent");
        });
  }

  @Test
  void writeOff_blank_reason_throws() {
    runInTenant(
        () -> {
          var created =
              disbursementService.create(officeRequest(DisbursementCategory.OTHER, null), memberId);
          assertThatThrownBy(() -> disbursementService.writeOff(created.id(), "   "))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void writeOff_BILLED_throws() {
    final UUID[] lineIdHolder = new UUID[1];
    runInTenant(
        () -> transactionTemplate.executeWithoutResult(tx -> lineIdHolder[0] = seedInvoiceLine()));

    runInTenant(
        () -> {
          var created =
              disbursementService.create(officeRequest(DisbursementCategory.OTHER, null), memberId);
          disbursementService.submitForApproval(created.id());
          disbursementService.approve(created.id(), memberId, null);
          disbursementService.markBilled(created.id(), lineIdHolder[0]);

          assertThatThrownBy(() -> disbursementService.writeOff(created.id(), "too late"))
              .isInstanceOf(ResourceConflictException.class);
        });
  }

  @Test
  void markBilled_publishesDisbursementBilledEvent_withInvoiceLineId() {
    final UUID[] lineIdHolder = new UUID[1];
    runInTenant(
        () -> transactionTemplate.executeWithoutResult(tx -> lineIdHolder[0] = seedInvoiceLine()));

    runInTenant(
        () -> {
          var created =
              disbursementService.create(officeRequest(DisbursementCategory.OTHER, null), memberId);
          disbursementService.submitForApproval(created.id());
          disbursementService.approve(created.id(), memberId, null);

          var invoiceLineId = lineIdHolder[0];
          var billed = disbursementService.markBilled(created.id(), invoiceLineId);
          assertThat(billed.billingStatus()).isEqualTo(DisbursementBillingStatus.BILLED);
          assertThat(billed.invoiceLineId()).isEqualTo(invoiceLineId);

          long count =
              events.stream(DisbursementBilledEvent.class)
                  .filter(e -> e.disbursementId().equals(created.id()))
                  .filter(e -> invoiceLineId.equals(e.invoiceLineId()))
                  .count();
          assertThat(count).isEqualTo(1);
        });
  }

  @Test
  void listForStatement_respectsDateRangeAndOrderByCategoryThenDate() {
    runInTenant(
        () -> {
          // Early March: counsel
          var a =
              disbursementService.create(
                  officeRequest(
                      DisbursementCategory.COUNSEL_FEES,
                      VatTreatment.STANDARD_15,
                      LocalDate.of(2026, 3, 5),
                      "counsel early"),
                  memberId);
          // Late March: counsel
          var b =
              disbursementService.create(
                  officeRequest(
                      DisbursementCategory.COUNSEL_FEES,
                      VatTreatment.STANDARD_15,
                      LocalDate.of(2026, 3, 20),
                      "counsel late"),
                  memberId);
          // Mid March: court
          var c =
              disbursementService.create(
                  officeRequest(
                      DisbursementCategory.COURT_FEES,
                      VatTreatment.ZERO_RATED_PASS_THROUGH,
                      LocalDate.of(2026, 3, 10),
                      "court mid"),
                  memberId);
          // February: outside range
          disbursementService.create(
              officeRequest(
                  DisbursementCategory.COURT_FEES,
                  VatTreatment.ZERO_RATED_PASS_THROUGH,
                  LocalDate.of(2026, 2, 28),
                  "court before"),
              memberId);

          var result =
              disbursementService.listForStatement(
                  projectId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

          assertThat(result).hasSize(3);
          // COUNSEL_FEES < COURT_FEES alphabetically; inside category order by incurredDate
          assertThat(result.get(0).id()).isEqualTo(a.id());
          assertThat(result.get(1).id()).isEqualTo(b.id());
          assertThat(result.get(2).id()).isEqualTo(c.id());
        });
  }

  // ==========================================================================
  // 486.15 — trust-link validation (both OFFICE_ACCOUNT / TRUST_ACCOUNT branches)
  // ==========================================================================

  @Test
  void create_OFFICE_ACCOUNT_nullTrustTxn_succeeds() {
    runInTenant(
        () -> {
          var response =
              disbursementService.create(
                  officeRequest(DisbursementCategory.SHERIFF_FEES, null), memberId);
          assertThat(response.paymentSource()).isEqualTo(DisbursementPaymentSource.OFFICE_ACCOUNT);
          assertThat(response.trustTransactionId()).isNull();
        });
  }

  @Test
  void create_OFFICE_ACCOUNT_nonNullTrustTxn_failsAtServiceValidation() {
    runInTenant(
        () -> {
          var req =
              new CreateDisbursementRequest(
                  projectId,
                  customerId,
                  DisbursementCategory.SHERIFF_FEES,
                  "bad link",
                  new BigDecimal("250.00"),
                  null,
                  DisbursementPaymentSource.OFFICE_ACCOUNT,
                  UUID.randomUUID(),
                  LocalDate.of(2026, 4, 1),
                  "Sheriff Johannesburg",
                  null,
                  null);

          assertThatThrownBy(() -> disbursementService.create(req, memberId))
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("OFFICE_ACCOUNT");
        });
  }

  @Test
  void create_TRUST_ACCOUNT_approvedPaymentSameMatter_succeeds() {
    final UUID[] trustTxIdHolder = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var trustTx = seedApprovedTrustTransaction(projectId);
                  trustTxIdHolder[0] = trustTx.getId();
                }));

    runInTenant(
        () -> {
          var req =
              new CreateDisbursementRequest(
                  projectId,
                  customerId,
                  DisbursementCategory.SHERIFF_FEES,
                  "linked to trust",
                  new BigDecimal("250.00"),
                  null,
                  DisbursementPaymentSource.TRUST_ACCOUNT,
                  trustTxIdHolder[0],
                  LocalDate.of(2026, 4, 1),
                  "Sheriff Johannesburg",
                  null,
                  null);
          var response = disbursementService.create(req, memberId);
          assertThat(response.paymentSource()).isEqualTo(DisbursementPaymentSource.TRUST_ACCOUNT);
          assertThat(response.trustTransactionId()).isEqualTo(trustTxIdHolder[0]);
        });
  }

  @Test
  void create_TRUST_ACCOUNT_trustTxnDifferentProject_failsValidation() {
    final UUID[] trustTxIdHolder = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Seed on a DIFFERENT project
                  var trustTx = seedApprovedTrustTransaction(otherProjectId);
                  trustTxIdHolder[0] = trustTx.getId();
                }));

    runInTenant(
        () -> {
          var req =
              new CreateDisbursementRequest(
                  projectId,
                  customerId,
                  DisbursementCategory.SHERIFF_FEES,
                  "wrong-project link",
                  new BigDecimal("250.00"),
                  null,
                  DisbursementPaymentSource.TRUST_ACCOUNT,
                  trustTxIdHolder[0],
                  LocalDate.of(2026, 4, 1),
                  "Sheriff Johannesburg",
                  null,
                  null);

          assertThatThrownBy(() -> disbursementService.create(req, memberId))
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("different project");
        });
  }

  @Test
  void create_TRUST_ACCOUNT_trustTxnNotApproved_failsValidation() {
    final UUID[] trustTxIdHolder = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Seed AWAITING_APPROVAL (not APPROVED)
                  var trustTx =
                      new TrustTransaction(
                          trustAccountId,
                          "PAYMENT",
                          new BigDecimal("1000.00"),
                          customerId,
                          projectId,
                          null,
                          "TRUSTREF-PEND-" + UUID.randomUUID(),
                          "Pending trust payment",
                          LocalDate.of(2026, 4, 1),
                          "AWAITING_APPROVAL",
                          memberId);
                  trustTx = trustTransactionRepository.saveAndFlush(trustTx);
                  trustTxIdHolder[0] = trustTx.getId();
                }));

    runInTenant(
        () -> {
          var req =
              new CreateDisbursementRequest(
                  projectId,
                  customerId,
                  DisbursementCategory.SHERIFF_FEES,
                  "not-approved link",
                  new BigDecimal("250.00"),
                  null,
                  DisbursementPaymentSource.TRUST_ACCOUNT,
                  trustTxIdHolder[0],
                  LocalDate.of(2026, 4, 1),
                  "Sheriff Johannesburg",
                  null,
                  null);

          assertThatThrownBy(() -> disbursementService.create(req, memberId))
              .isInstanceOf(InvalidStateException.class)
              .hasMessageContaining("APPROVED");
        });
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  private CreateDisbursementRequest officeRequest(
      DisbursementCategory category, VatTreatment vatTreatment) {
    return officeRequest(category, vatTreatment, LocalDate.of(2026, 4, 1), "standard item");
  }

  private CreateDisbursementRequest officeRequest(
      DisbursementCategory category,
      VatTreatment vatTreatment,
      LocalDate incurredDate,
      String description) {
    return new CreateDisbursementRequest(
        projectId,
        customerId,
        category,
        description,
        new BigDecimal("250.00"),
        vatTreatment,
        DisbursementPaymentSource.OFFICE_ACCOUNT,
        null,
        incurredDate,
        "Supplier Co",
        "REF-001",
        null);
  }

  /**
   * Persists a trust transaction directly via the repository — the CHECK constraint allows {@code
   * transactionType='PAYMENT'} and {@code status='APPROVED'}, which is what {@link
   * DisbursementService#validateTrustLink} expects.
   */
  private TrustTransaction seedApprovedTrustTransaction(UUID projectForTx) {
    var tx =
        new TrustTransaction(
            trustAccountId,
            "PAYMENT",
            new BigDecimal("1000.00"),
            customerId,
            projectForTx,
            null,
            "TRUSTREF-" + UUID.randomUUID(),
            "Approved trust payment",
            LocalDate.of(2026, 4, 1),
            "APPROVED",
            memberId);
    return trustTransactionRepository.saveAndFlush(tx);
  }

  /** Persists a real Invoice + InvoiceLine so markBilled FK resolves. Returns the line id. */
  private UUID seedInvoiceLine() {
    var invoice =
        new Invoice(
            customerId,
            "USD",
            "Disbursement Svc Client",
            "dsvc@test.com",
            null,
            "Disbursement Svc Test",
            memberId);
    var savedInvoice = invoiceRepository.saveAndFlush(invoice);
    var line =
        new InvoiceLine(
            savedInvoice.getId(),
            projectId,
            null,
            "Disbursement line",
            new BigDecimal("1"),
            new BigDecimal("100.00"),
            0);
    return invoiceLineRepository.saveAndFlush(line).getId();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}

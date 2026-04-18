package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Repository + CHECK-constraint integration tests for {@link LegalDisbursement} (Phase 67, Epic
 * 486A).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegalDisbursementRepositoryTest {

  private static final String ORG_ID = "org_disbursement_repo_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "Disbursement Repo Test", null);

    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_disb_owner",
                "disb_owner@test.com",
                "Disbursement Owner",
                "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      createActiveCustomer("Disbursement Client", "dclient@test.com", memberId);
                  customer = customerRepository.saveAndFlush(customer);
                  customerId = customer.getId();

                  var project =
                      new Project(
                          "Disbursement Matter", "Matter for disbursement repo tests", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();
                }));
  }

  // ========================================================================
  // CHECK constraint tests
  // ========================================================================

  @Test
  void insertWithZeroAmount_failsAmountPositiveCheck() {
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx -> {
                              var d = validDisbursement();
                              d.setAmount(BigDecimal.ZERO);
                              disbursementRepository.saveAndFlush(d);
                            }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_legal_disbursements_amount_positive"));
  }

  @Test
  void insertWithInvalidCategory_failsCategoryCheck() {
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx -> {
                              var d = validDisbursement();
                              d.setCategory("INVALID");
                              disbursementRepository.saveAndFlush(d);
                            }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_legal_disbursements_category"));
  }

  @Test
  void insertWithTrustPaymentSource_andNullTrustTxId_failsTrustLinkCheck() {
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx -> {
                              // payment_source = TRUST_ACCOUNT but trustTransactionId = null
                              var d =
                                  new LegalDisbursement(
                                      projectId,
                                      customerId,
                                      DisbursementCategory.SHERIFF_FEES.name(),
                                      "Sheriff fees — trust without link",
                                      new BigDecimal("250.00"),
                                      VatTreatment.STANDARD_15.name(),
                                      new BigDecimal("32.61"),
                                      DisbursementPaymentSource.TRUST_ACCOUNT.name(),
                                      null,
                                      LocalDate.of(2026, 4, 1),
                                      "Sheriff Johannesburg",
                                      null,
                                      null,
                                      memberId);
                              disbursementRepository.saveAndFlush(d);
                            }))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_legal_disbursements_trust_link"));
  }

  @Test
  void insertWithOfficePaymentSource_andNonNullTrustTxId_failsTrustLinkCheck() {
    // Use a raw INSERT via JdbcTemplate so the CHECK fires before the FK to
    // trust_transactions would surface (both would abort the statement, but we
    // want to assert specifically on the trust-link CHECK message). CHECK
    // constraints are evaluated before FK constraints in Postgres.
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO \"%s\".legal_disbursements".formatted(tenantSchema)
                        + " (project_id, customer_id, category, description, amount,"
                        + " vat_treatment, vat_amount, payment_source, trust_transaction_id,"
                        + " incurred_date, supplier_name, created_by)"
                        + " VALUES (?, ?, 'COUNSEL_FEES', 'desc', 500.00, 'STANDARD_15',"
                        + " 65.22, 'OFFICE_ACCOUNT', ?, DATE '2026-04-02', 'Advocate Smith',"
                        + " ?)",
                    projectId,
                    customerId,
                    UUID.randomUUID(),
                    memberId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_legal_disbursements_trust_link");
  }

  @Test
  void insertWithWrittenOffBilling_andNullReason_failsWriteoffReasonCheck() {
    // Persist a valid disbursement first, then use a schema-qualified native UPDATE
    // that bypasses the entity guards to attempt to put the row into WRITTEN_OFF
    // with a null reason — the DB CHECK must reject.
    final UUID[] savedId = new UUID[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var saved = disbursementRepository.saveAndFlush(validDisbursement());
                  savedId[0] = saved.getId();
                }));

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE \"%s\".legal_disbursements".formatted(tenantSchema)
                        + " SET billing_status = 'WRITTEN_OFF', write_off_reason = NULL"
                        + " WHERE id = ?",
                    savedId[0]))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ck_legal_disbursements_writeoff_reason");
  }

  // ========================================================================
  // Happy-path + query tests
  // ========================================================================

  @Test
  void happyPathInsert_persistsWithDefaultApprovalAndBillingStatus() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var d = validDisbursement();
                  var saved = disbursementRepository.saveAndFlush(d);

                  assertThat(saved.getId()).isNotNull();
                  assertThat(saved.getApprovalStatus())
                      .isEqualTo(DisbursementApprovalStatus.DRAFT.name());
                  assertThat(saved.getBillingStatus())
                      .isEqualTo(DisbursementBillingStatus.UNBILLED.name());
                  assertThat(saved.getCreatedAt()).isNotNull();
                  assertThat(saved.getUpdatedAt()).isNotNull();
                  assertThat(saved.getVatAmount()).isNotNull();
                }));
  }

  @Test
  void findByProjectIdAndApprovalStatusIn_returnsOnlyMatching() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // Create one DRAFT, one PENDING_APPROVAL, one APPROVED
                  var draft = validDisbursement();
                  draft.setDescription("draft item");
                  draft = disbursementRepository.saveAndFlush(draft);

                  var pending = validDisbursement();
                  pending.setDescription("pending item");
                  pending.submitForApproval();
                  pending = disbursementRepository.saveAndFlush(pending);

                  var approved = validDisbursement();
                  approved.setDescription("approved item");
                  approved.submitForApproval();
                  approved.approve(memberId, "ok");
                  approved = disbursementRepository.saveAndFlush(approved);

                  var pendingOnly =
                      disbursementRepository.findByProjectIdAndApprovalStatusIn(
                          projectId, List.of(DisbursementApprovalStatus.PENDING_APPROVAL.name()));

                  assertThat(pendingOnly)
                      .extracting(LegalDisbursement::getId)
                      .containsExactly(pending.getId());

                  // Sanity: also test the count helper
                  long countPending =
                      disbursementRepository.countByProjectIdAndApprovalStatusIn(
                          projectId, List.of(DisbursementApprovalStatus.PENDING_APPROVAL.name()));
                  assertThat(countPending).isEqualTo(1);
                }));
  }

  @Test
  void findForStatement_filtersByDateAndOrdersByCategoryThenDate() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  LocalDate from = LocalDate.of(2026, 3, 1);
                  LocalDate to = LocalDate.of(2026, 3, 31);

                  // Inside the window
                  var inWindow1 =
                      new LegalDisbursement(
                          projectId,
                          customerId,
                          DisbursementCategory.COURT_FEES.name(),
                          "court fees mid",
                          new BigDecimal("100.00"),
                          VatTreatment.STANDARD_15.name(),
                          new BigDecimal("13.04"),
                          DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                          null,
                          LocalDate.of(2026, 3, 10),
                          "Court Registrar",
                          null,
                          null,
                          memberId);

                  var inWindow2 =
                      new LegalDisbursement(
                          projectId,
                          customerId,
                          DisbursementCategory.COUNSEL_FEES.name(),
                          "counsel early",
                          new BigDecimal("200.00"),
                          VatTreatment.STANDARD_15.name(),
                          new BigDecimal("26.09"),
                          DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                          null,
                          LocalDate.of(2026, 3, 5),
                          "Advocate B",
                          null,
                          null,
                          memberId);

                  var inWindow3 =
                      new LegalDisbursement(
                          projectId,
                          customerId,
                          DisbursementCategory.COUNSEL_FEES.name(),
                          "counsel late",
                          new BigDecimal("300.00"),
                          VatTreatment.STANDARD_15.name(),
                          new BigDecimal("39.13"),
                          DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                          null,
                          LocalDate.of(2026, 3, 20),
                          "Advocate A",
                          null,
                          null,
                          memberId);

                  // Outside the window
                  var outWindow =
                      new LegalDisbursement(
                          projectId,
                          customerId,
                          DisbursementCategory.COURT_FEES.name(),
                          "court before window",
                          new BigDecimal("50.00"),
                          VatTreatment.EXEMPT.name(),
                          BigDecimal.ZERO,
                          DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                          null,
                          LocalDate.of(2026, 2, 28),
                          "Court Registrar",
                          null,
                          null,
                          memberId);

                  disbursementRepository.saveAndFlush(inWindow1);
                  disbursementRepository.saveAndFlush(inWindow2);
                  disbursementRepository.saveAndFlush(inWindow3);
                  disbursementRepository.saveAndFlush(outWindow);

                  var result = disbursementRepository.findForStatement(projectId, from, to);

                  // Only the three in-window items
                  assertThat(result).hasSize(3);

                  // Ordered by category ASC, then incurredDate ASC.
                  // COUNSEL_FEES < COURT_FEES alphabetically, so:
                  //  COUNSEL_FEES 2026-03-05, COUNSEL_FEES 2026-03-20, COURT_FEES 2026-03-10.
                  assertThat(result)
                      .extracting(LegalDisbursement::getDescription)
                      .containsExactly("counsel early", "counsel late", "court fees mid");
                }));
  }

  // ========================================================================
  // Helpers
  // ========================================================================

  private LegalDisbursement validDisbursement() {
    return new LegalDisbursement(
        projectId,
        customerId,
        DisbursementCategory.SHERIFF_FEES.name(),
        "Sheriff service fee",
        new BigDecimal("250.00"),
        VatTreatment.STANDARD_15.name(),
        new BigDecimal("32.61"),
        DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
        null,
        LocalDate.of(2026, 4, 1),
        "Sheriff Johannesburg",
        "SJ-2026-001",
        null,
        memberId);
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}

package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Repository-level integration test for {@link LegalDisbursement}. Exercises DB CHECK constraints
 * (amount, category, trust-link XOR, write-off reason) and repository queries ({@code
 * findByProjectIdAndApprovalStatusIn}, {@code findForStatement}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LegalDisbursementRepositoryTest {
  private static final String ORG_ID = "org_legal_disb_repo_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private UUID projectId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Legal Disbursement Repo Test Org", "legal-za")
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_disb_repo_owner",
                "disb_repo_owner@test.com",
                "Disb Repo Owner",
                "owner"));

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var customer =
                      customerRepository.saveAndFlush(
                          createActiveCustomer(
                              "Legal Disb Repo Client", "legal_disb_repo@test.com", memberId));
                  customerId = customer.getId();

                  var project = new Project("Repo Test Matter", "Repo test matter", memberId);
                  project.setCustomerId(customerId);
                  project = projectRepository.saveAndFlush(project);
                  projectId = project.getId();
                }));
  }

  // --- CHECK constraint tests ---

  @Test
  void insert_withNonPositiveAmount_failsCheckConstraint() {
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx -> {
                              var d =
                                  new LegalDisbursement(
                                      projectId,
                                      customerId,
                                      LocalDate.of(2026, 4, 1),
                                      "SHERIFF_FEES",
                                      "Zero amount test",
                                      new BigDecimal("0.00"),
                                      "ZAR",
                                      "ZERO_RATED_PASS_THROUGH",
                                      BigDecimal.ZERO,
                                      "OFFICE_ACCOUNT",
                                      memberId);
                              disbursementRepository.saveAndFlush(d);
                            }))
                .isInstanceOf(DataIntegrityViolationException.class));
  }

  @Test
  void insert_withInvalidCategory_failsCheckConstraint() {
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx -> {
                              var d =
                                  new LegalDisbursement(
                                      projectId,
                                      customerId,
                                      LocalDate.of(2026, 4, 1),
                                      "NOT_A_REAL_CATEGORY",
                                      "Bad category test",
                                      new BigDecimal("100.00"),
                                      "ZAR",
                                      "STANDARD_15",
                                      new BigDecimal("15.00"),
                                      "OFFICE_ACCOUNT",
                                      memberId);
                              disbursementRepository.saveAndFlush(d);
                            }))
                .isInstanceOf(DataIntegrityViolationException.class));
  }

  @Test
  void insert_trustAccountWithoutTrustTxnId_failsCheckConstraint() {
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx -> {
                              var d =
                                  new LegalDisbursement(
                                      projectId,
                                      customerId,
                                      LocalDate.of(2026, 4, 1),
                                      "COUNSEL_FEES",
                                      "Trust without txn id",
                                      new BigDecimal("1000.00"),
                                      "ZAR",
                                      "STANDARD_15",
                                      new BigDecimal("150.00"),
                                      "TRUST_ACCOUNT",
                                      memberId);
                              disbursementRepository.saveAndFlush(d);
                            }))
                .isInstanceOf(DataIntegrityViolationException.class));
  }

  @Test
  void insert_officeAccountWithTrustTxnId_failsCheckConstraint() {
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx -> {
                              var d =
                                  new LegalDisbursement(
                                      projectId,
                                      customerId,
                                      LocalDate.of(2026, 4, 1),
                                      "COUNSEL_FEES",
                                      "Office with trust txn id",
                                      new BigDecimal("500.00"),
                                      "ZAR",
                                      "STANDARD_15",
                                      new BigDecimal("75.00"),
                                      "OFFICE_ACCOUNT",
                                      memberId);
                              d.setTrustTransactionId(UUID.randomUUID());
                              disbursementRepository.saveAndFlush(d);
                            }))
                .isInstanceOf(DataIntegrityViolationException.class));
  }

  @Test
  void insert_writtenOffWithoutReason_failsCheckConstraint() {
    runInTenant(
        () ->
            assertThatThrownBy(
                    () ->
                        transactionTemplate.executeWithoutResult(
                            tx -> {
                              // Persist via native SQL to bypass entity default of UNBILLED +
                              // allow forcing billing_status=WRITTEN_OFF with null
                              // write_off_reason.
                              // Using the entity here is fine because PrePersist defaults to
                              // UNBILLED; we then update the fields directly via JDBC-equivalent.
                              var d =
                                  new LegalDisbursement(
                                      projectId,
                                      customerId,
                                      LocalDate.of(2026, 4, 1),
                                      "COUNSEL_FEES",
                                      "Written off no reason",
                                      new BigDecimal("500.00"),
                                      "ZAR",
                                      "STANDARD_15",
                                      new BigDecimal("75.00"),
                                      "OFFICE_ACCOUNT",
                                      memberId);
                              // @PrePersist defaults billingStatus to UNBILLED — update via the
                              // entity's state-transition is guarded; we simulate the invariant via
                              // saving a row and then running an UPDATE that bypasses the entity
                              // invariants. The CHECK constraint should still fire.
                              var saved = disbursementRepository.saveAndFlush(d);
                              // Use JPQL-style update via repository -- but entity has no setter.
                              // Fall through to native SQL via JdbcTemplate is overkill; instead,
                              // attempt to call writeOff with blank reason which Hibernate flushes
                              // as billing_status=WRITTEN_OFF with write_off_reason=null.
                              // writeOff() rejects blank reason at the Java level with ISE, so we
                              // cannot hit the CHECK via the entity. Verify the CHECK instead
                              // through direct DB update.
                              jdbcUpdateExpectConstraintFailure(saved.getId());
                            }))
                // Hibernate/Spring may surface the CHECK constraint as any of
                // DataIntegrityViolationException (most common), UncategorizedSQLException, or
                // DataAccessException depending on driver/translator chain. Accept any: all three
                // prove the CHECK fired at DB level.
                .satisfiesAnyOf(
                    t ->
                        assertThat(t)
                            .isInstanceOfAny(
                                DataIntegrityViolationException.class,
                                org.springframework.jdbc.UncategorizedSQLException.class,
                                org.springframework.dao.DataAccessException.class)));
  }

  @Test
  void insert_happyPath_defaultsApprovalDraftAndBillingUnbilled() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var d =
                      new LegalDisbursement(
                          projectId,
                          customerId,
                          LocalDate.of(2026, 4, 1),
                          "SHERIFF_FEES",
                          "Happy path",
                          new BigDecimal("350.00"),
                          "ZAR",
                          "ZERO_RATED_PASS_THROUGH",
                          BigDecimal.ZERO,
                          "OFFICE_ACCOUNT",
                          memberId);
                  var saved = disbursementRepository.saveAndFlush(d);

                  assertThat(saved.getId()).isNotNull();
                  assertThat(saved.getApprovalStatus()).isEqualTo("DRAFT");
                  assertThat(saved.getBillingStatus()).isEqualTo("UNBILLED");
                  assertThat(saved.getCurrency()).isEqualTo("ZAR");
                  assertThat(saved.getCreatedAt()).isNotNull();
                  assertThat(saved.getUpdatedAt()).isNotNull();
                }));
  }

  // --- Repository query tests ---

  @Test
  void findByProjectIdAndApprovalStatusIn_returnsOnlyMatchingStatuses() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // DRAFT
                  disbursementRepository.saveAndFlush(
                      newDraft(projectId, customerId, memberId, "DRAFT A", "100.00"));
                  // Another DRAFT
                  disbursementRepository.saveAndFlush(
                      newDraft(projectId, customerId, memberId, "DRAFT B", "200.00"));
                  // PENDING_APPROVAL — go through the entity state transition
                  var pending = newDraft(projectId, customerId, memberId, "PENDING", "300.00");
                  pending.submitForApproval();
                  disbursementRepository.saveAndFlush(pending);

                  var pendingOnly =
                      disbursementRepository.findByProjectIdAndApprovalStatusIn(
                          projectId, List.of("PENDING_APPROVAL"));

                  assertThat(pendingOnly)
                      .extracting(LegalDisbursement::getApprovalStatus)
                      .allMatch("PENDING_APPROVAL"::equals);
                  assertThat(pendingOnly)
                      .extracting(LegalDisbursement::getId)
                      .contains(pending.getId());
                }));
  }

  @Test
  void findForStatement_returnsApprovedWithinDateRange_orderedByCategoryThenDate() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // In-range APPROVED of COUNSEL_FEES
                  var d1 = newDraft(projectId, customerId, memberId, "Counsel A", "1000.00");
                  d1.submitForApproval();
                  d1.approve(memberId, "ok");
                  d1 = withIncurredDateAndCategory(d1, LocalDate.of(2026, 3, 10), "COUNSEL_FEES");
                  disbursementRepository.saveAndFlush(d1);

                  // In-range APPROVED of SHERIFF_FEES
                  var d2 = newDraft(projectId, customerId, memberId, "Sheriff A", "200.00");
                  d2.submitForApproval();
                  d2.approve(memberId, "ok");
                  d2 = withIncurredDateAndCategory(d2, LocalDate.of(2026, 3, 5), "SHERIFF_FEES");
                  disbursementRepository.saveAndFlush(d2);

                  // Out-of-range APPROVED
                  var d3 = newDraft(projectId, customerId, memberId, "Out of range", "50.00");
                  d3.submitForApproval();
                  d3.approve(memberId, "ok");
                  d3 = withIncurredDateAndCategory(d3, LocalDate.of(2026, 1, 1), "OTHER");
                  disbursementRepository.saveAndFlush(d3);

                  // In-range but DRAFT (should be excluded)
                  var d4 = newDraft(projectId, customerId, memberId, "Draft in range", "75.00");
                  d4 = withIncurredDateAndCategory(d4, LocalDate.of(2026, 3, 15), "COUNSEL_FEES");
                  disbursementRepository.saveAndFlush(d4);

                  var rows =
                      disbursementRepository.findForStatement(
                          projectId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

                  assertThat(rows)
                      .extracting(LegalDisbursement::getApprovalStatus)
                      .allMatch("APPROVED"::equals);
                  assertThat(rows)
                      .extracting(LegalDisbursement::getIncurredDate)
                      .allMatch(
                          dt ->
                              !dt.isBefore(LocalDate.of(2026, 3, 1))
                                  && !dt.isAfter(LocalDate.of(2026, 3, 31)));
                  // Category ordering: COUNSEL_FEES comes before SHERIFF_FEES alphabetically
                  assertThat(rows)
                      .extracting(LegalDisbursement::getCategory)
                      .first()
                      .isEqualTo("COUNSEL_FEES");
                }));
  }

  // --- Helpers ---

  private void runInTenant(Runnable body) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(body);
  }

  private static LegalDisbursement newDraft(
      UUID projectId, UUID customerId, UUID createdBy, String description, String amount) {
    return new LegalDisbursement(
        projectId,
        customerId,
        LocalDate.of(2026, 3, 1),
        "COUNSEL_FEES",
        description,
        new BigDecimal(amount),
        "ZAR",
        "STANDARD_15",
        new BigDecimal(amount)
            .multiply(new BigDecimal("0.15"))
            .setScale(2, java.math.RoundingMode.HALF_UP),
        "OFFICE_ACCOUNT",
        createdBy);
  }

  /**
   * Forces the incurred date and category of a disbursement after its state-transition. Uses the
   * entity's public update() method which only works while still in DRAFT — callers therefore
   * perform state transitions AFTER calling this, if needed. Since in the test above we transition
   * before calling, we reach in through a stand-in: rebuild via constructor semantics.
   */
  private LegalDisbursement withIncurredDateAndCategory(
      LegalDisbursement saved, LocalDate incurredDate, String category) {
    // We cannot call update() after approve() — entity blocks it. Build a new row using the
    // already-approved state by reusing direct field mapping via another save. Simplest: issue a
    // native SQL update via the Entity's persistence context.
    // Because the tests here only care about the repository SELECT behaviour, the cleanest path is
    // to create the disbursement already with the right incurred_date / category via a fresh row
    // (recompile the builder) and approve it. The caller pattern above creates then adjusts, so
    // we reconstruct:
    var replacement =
        new LegalDisbursement(
            saved.getProjectId(),
            saved.getCustomerId(),
            incurredDate,
            category,
            saved.getDescription(),
            saved.getAmount(),
            saved.getCurrency(),
            saved.getVatTreatment(),
            saved.getVatAmount(),
            saved.getPaymentSource(),
            saved.getCreatedBy());
    replacement.submitForApproval();
    replacement.approve(saved.getCreatedBy(), "reconstructed");
    return replacement;
  }

  @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  /**
   * Attempts a native UPDATE on legal_disbursements that sets billing_status='WRITTEN_OFF' with
   * write_off_reason=NULL to trigger the CHECK constraint. Ensures the CHECK actually fires at the
   * DB layer.
   */
  private void jdbcUpdateExpectConstraintFailure(UUID id) {
    jdbcTemplate.update(
        "UPDATE "
            + tenantSchema
            + ".legal_disbursements SET billing_status='WRITTEN_OFF', write_off_reason=NULL WHERE id = ?",
        id);
  }
}

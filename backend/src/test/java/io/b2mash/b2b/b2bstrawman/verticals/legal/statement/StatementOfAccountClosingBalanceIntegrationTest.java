package io.b2mash.b2b.b2bstrawman.verticals.legal.statement;

import static io.b2mash.b2b.b2bstrawman.testutil.TestCustomerFactory.createActiveCustomer;
import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineType;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceTransitionService;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentMethod;
import io.b2mash.b2b.b2bstrawman.invoice.PaymentPosting;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import io.b2mash.b2b.b2bstrawman.tax.TaxRate;
import io.b2mash.b2b.b2bstrawman.tax.TaxRateRepository;
import io.b2mash.b2b.b2bstrawman.template.GeneratedDocumentRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntry;
import io.b2mash.b2b.b2bstrawman.timeentry.TimeEntryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.MatterClosureContextBuilder;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.MatterClosureService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureReason;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementCategory;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementPaymentSource;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.VatTreatment;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * G89-F16 (founder decision D9): the Statement of Account closing balance is on a <b>fee-note
 * basis</b> — Σ issued (SENT / PARTIALLY_PAID / PAID) fee-note totals − COMPLETED payments up to
 * the period end — and it agrees with the closure letter, which reads the same fee notes. The time
 * entry and disbursement tables stay activity listings; written-off / unapproved disbursements are
 * never listed; unbilled work in progress is shown as its own summary key but never counted as
 * owing.
 *
 * <p>The golden scenario is the 636B exit-test matter: a paid fee note, a written-off R500
 * disbursement, a closure with the Statement of Account → "Closing balance owing: R0,00".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementOfAccountClosingBalanceIntegrationTest {

  private static final String ORG_ID = "org_soa_closing_balance";
  private static final String FIRM = "SoA Closing Balance Firm";
  private static final String CLIENT = "Closing Balance Client";
  private static final String JUSTIFICATION =
      "Matter concluded; the fee note is settled and the written-off disbursement stays off.";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private CustomerRepository customerRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TimeEntryRepository timeEntryRepository;
  @Autowired private DisbursementRepository disbursementRepository;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private InvoiceTransitionService invoiceTransitionService;
  @Autowired private TaxRateRepository taxRateRepository;
  @Autowired private StatementOfAccountContextBuilder statementContextBuilder;
  @Autowired private MatterClosureContextBuilder closureContextBuilder;
  @Autowired private MatterClosureService matterClosureService;
  @Autowired private GeneratedDocumentRepository generatedDocumentRepository;
  @Autowired private StorageService storageService;

  private String tenantSchema;
  private UUID memberId;
  private UUID customerId;
  private LocalDate today;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, FIRM, "legal-za");
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_soa_closing_owner",
                "soa_closing_owner@test.com",
                "SoA Closing Owner",
                "owner"));
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();
    today = LocalDate.now(KaziTime.ZONE);

    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsService.getOrCreateForCurrentTenant();
                  settings.setEnabledModules(List.of("disbursements", "matter_closure"));
                  settings.updateCurrency("ZAR");
                  orgSettingsRepository.save(settings);
                  customerId =
                      customerRepository
                          .saveAndFlush(
                              createActiveCustomer(CLIENT, "soa_closing@test.com", memberId))
                          .getId();
                }));
  }

  // ---------------------------------------------------------------------------------------------
  // A WRITTEN_OFF disbursement is not listed on the statement and is never owing.
  // ---------------------------------------------------------------------------------------------

  @Test
  void writtenOffDisbursement_notListed_notOwing() {
    UUID matterId = newMatter("Written-off Disbursement Matter");
    UUID writtenOff = disbursement(matterId, "500.00", "0.00", Disposition.WRITTEN_OFF);
    UUID draft = disbursement(matterId, "320.00", "0.00", Disposition.DRAFT);

    var context = build(matterId, today.minusDays(30), today);

    assertThat(disbursementIds(context))
        .as("the statement lists neither the written-off nor the unapproved disbursement")
        .doesNotContain(writtenOff.toString(), draft.toString());
    assertThat(summary(context, "closing_balance_owing"))
        .as("no fee note was issued, so nothing is owing")
        .isEqualByComparingTo("0");
  }

  // ---------------------------------------------------------------------------------------------
  // Golden (636B): paid fee note + written-off R500 disbursement + closure → closing R 0,00.
  // ---------------------------------------------------------------------------------------------

  @Test
  void paidFeeNote_closingBalanceZero() {
    UUID matterId = newMatter("636B Golden Matter");
    UUID timeEntryId = billableTime(matterId, 120, "1000.00");
    BigDecimal feeVat = vatOn(new BigDecimal("2000.00"));
    UUID noteId =
        issuedNote(
            matterId,
            List.of(
                new Line(InvoiceLineType.TIME, "Consultation", "2000.00", feeVat, timeEntryId)));
    BigDecimal noteTotal = new BigDecimal("2000.00").add(feeVat);
    pay(noteId, noteTotal);
    disbursement(matterId, "500.00", "0.00", Disposition.WRITTEN_OFF);

    var response =
        runInTenantReturning(
            () ->
                matterClosureService.close(
                    matterId,
                    new ClosureRequest(
                        ClosureReason.CONCLUDED,
                        null,
                        false,
                        /* generateStatementOfAccount */ true,
                        /* override */ true,
                        JUSTIFICATION,
                        null,
                        null),
                    memberId));
    assertThat(response.statementOfAccountDocumentId())
        .as("the closure generated the Statement of Account")
        .isNotNull();

    @SuppressWarnings("unchecked")
    Map<String, Object> snapshotSummary =
        runInTenantReturning(
            () ->
                (Map<String, Object>)
                    generatedDocumentRepository
                        .findById(response.statementOfAccountDocumentId())
                        .orElseThrow()
                        .getContextSnapshot()
                        .get("summary"));
    assertThat(new BigDecimal(snapshotSummary.get("closing_balance_owing").toString()))
        .as("a paid fee note and a written-off disbursement leave nothing owing")
        .isEqualByComparingTo("0");

    String text = pdfText(response.statementOfAccountDocumentId());
    assertThat(text).contains("Closing balance owing: R0,00").doesNotContain("R500,00");
  }

  // ---------------------------------------------------------------------------------------------
  // A tariff line on an issued fee note is billed work — it counts in fees and in the balance.
  // ---------------------------------------------------------------------------------------------

  @Test
  void tariffLineOnFeeNote_counted() {
    UUID matterId = newMatter("Tariff Line Matter");
    BigDecimal vat = vatOn(new BigDecimal("800.00"));
    issuedNote(
        matterId,
        List.of(new Line(InvoiceLineType.TARIFF, "Tariff: perusal", "800.00", vat, null)));
    BigDecimal noteTotal = new BigDecimal("800.00").add(vat);

    var context = build(matterId, today.minusDays(30), today);

    assertThat(summary(context, "total_fees"))
        .as("the tariff line's fee (incl. VAT) is in the period's fees")
        .isEqualByComparingTo(noteTotal);
    assertThat(summary(context, "closing_balance_owing"))
        .as("the unpaid fee note is owing in full")
        .isEqualByComparingTo(noteTotal);
  }

  // ---------------------------------------------------------------------------------------------
  // Unbilled work in progress is shown on its own summary key and is never owing.
  // ---------------------------------------------------------------------------------------------

  @Test
  void unbilledWip_shownNotOwing() {
    UUID matterId = newMatter("Unbilled WIP Matter");
    billableTime(matterId, 60, "600.00");
    disbursement(matterId, "200.00", "30.00", Disposition.APPROVED);
    disbursement(matterId, "450.00", "0.00", Disposition.WRITTEN_OFF);

    var context = build(matterId, today.minusDays(30), today);

    assertThat(summary(context, "unbilled_wip"))
        .as(
            "WIP = unbilled billable time (R600) + approved unbilled disbursements (R200), excl. VAT")
        .isEqualByComparingTo("800.00");
    assertThat(summary(context, "closing_balance_owing"))
        .as("nothing has been billed, so nothing is owing")
        .isEqualByComparingTo("0");
  }

  // ---------------------------------------------------------------------------------------------
  // The closure letter and the statement read the same issued fee notes.
  // ---------------------------------------------------------------------------------------------

  @Test
  void closureLetterAndSoa_agreeOnBilledTotals() {
    UUID matterId = newMatter("Letter And SoA Agree Matter");
    BigDecimal feeVat = vatOn(new BigDecimal("1800.00"));
    UUID noteId =
        issuedNote(
            matterId,
            List.of(
                new Line(InvoiceLineType.FIXED_FEE, "Consultation", "1800.00", feeVat, null),
                new Line(
                    InvoiceLineType.DISBURSEMENT,
                    "Sheriff fees: service",
                    "750.00",
                    BigDecimal.ZERO,
                    null)));
    pay(noteId, new BigDecimal("1000.00"));
    BigDecimal noteTotal = new BigDecimal("2550.00").add(feeVat);

    var soa = build(matterId, today.minusDays(30), today);
    Map<String, Object> letter =
        runInTenantReturning(
            () ->
                closureContextBuilder.buildClosureContext(
                    matterId,
                    new ClosureRequest(
                        ClosureReason.CONCLUDED, null, true, false, false, null, null, null)));
    @SuppressWarnings("unchecked")
    Map<String, Object> letterMatter = (Map<String, Object>) letter.get("matter");
    BigDecimal letterBilled =
        ((BigDecimal) letterMatter.get("total_fees_billed"))
            .add((BigDecimal) letterMatter.get("total_vat"))
            .add((BigDecimal) letterMatter.get("total_disbursements"));

    assertThat(letterBilled).isEqualByComparingTo(noteTotal);
    assertThat(summary(soa, "total_fees").add(summary(soa, "total_disbursements")))
        .as("the statement's billed fees + disbursements equal the letter's billed total")
        .isEqualByComparingTo(letterBilled);
    assertThat(summary(soa, "total_disbursements")).isEqualByComparingTo("750.00");
    assertThat(summary(soa, "payments_received")).isEqualByComparingTo("1000.00");
    assertThat(summary(soa, "closing_balance_owing"))
        .isEqualByComparingTo(noteTotal.subtract(new BigDecimal("1000.00")));
    assertThat(letterMatter.get("balance_owing"))
        .as("the letter exposes the same balance owing as the statement")
        .isNotNull();
    assertThat((BigDecimal) letterMatter.get("balance_owing"))
        .isEqualByComparingTo(summary(soa, "closing_balance_owing"));
  }

  // =============================================================================================
  // Fixtures
  // =============================================================================================

  private enum Disposition {
    DRAFT,
    APPROVED,
    WRITTEN_OFF
  }

  private record Line(
      InvoiceLineType type, String description, String amount, BigDecimal tax, UUID timeEntryId) {}

  private UUID newMatter(String name) {
    return runInTenantReturning(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var matter = new Project(name, "G89-F16 closing balance", memberId);
                  matter.setCustomerId(customerId);
                  return projectRepository.saveAndFlush(matter).getId();
                }));
  }

  /** A billable time entry dated today at {@code rate} per hour; returns its id. */
  private UUID billableTime(UUID matterId, int minutes, String rate) {
    return runInTenantReturning(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var task =
                      taskRepository.saveAndFlush(
                          new Task(matterId, "Work", null, "MEDIUM", "TASK", null, memberId));
                  var entry =
                      new TimeEntry(task.getId(), memberId, today, minutes, true, null, "Work");
                  entry.snapshotBillingRate(new BigDecimal(rate), "ZAR");
                  return timeEntryRepository.saveAndFlush(entry).getId();
                }));
  }

  private UUID disbursement(UUID matterId, String amount, String vat, Disposition disposition) {
    return runInTenantReturning(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var d =
                      new LegalDisbursement(
                          matterId,
                          customerId,
                          DisbursementCategory.SHERIFF_FEES.name(),
                          "Sheriff " + disposition,
                          new BigDecimal(amount),
                          new BigDecimal(vat).signum() > 0
                              ? VatTreatment.STANDARD_15.name()
                              : VatTreatment.ZERO_RATED_PASS_THROUGH.name(),
                          new BigDecimal(vat),
                          DisbursementPaymentSource.OFFICE_ACCOUNT.name(),
                          null,
                          today,
                          "Sheriff Johannesburg",
                          null,
                          null,
                          memberId);
                  if (disposition != Disposition.DRAFT) {
                    d.submitForApproval();
                    d.approve(memberId, null);
                  }
                  if (disposition == Disposition.WRITTEN_OFF) {
                    d.writeOff("Firm absorbs the cost");
                  }
                  return disbursementRepository.saveAndFlush(d).getId();
                }));
  }

  /** A SENT fee note on the matter carrying {@code lines} (tax-exclusive); returns its id. */
  private UUID issuedNote(UUID matterId, List<Line> lines) {
    TaxRate rate = runInTenantReturning(this::defaultRate);
    return runInTenantReturning(
        () ->
            transactionTemplate.execute(
                tx -> {
                  var note =
                      invoiceRepository.saveAndFlush(
                          new Invoice(customerId, "ZAR", CLIENT, null, null, FIRM, memberId));
                  BigDecimal subtotal = BigDecimal.ZERO;
                  BigDecimal tax = BigDecimal.ZERO;
                  int sort = 0;
                  for (Line l : lines) {
                    var line =
                        new InvoiceLine(
                            note.getId(),
                            matterId,
                            l.timeEntryId(),
                            l.description(),
                            BigDecimal.ONE,
                            new BigDecimal(l.amount()),
                            sort++);
                    line.setLineType(l.type());
                    if (l.tax().signum() > 0) {
                      line.applyTaxRate(rate, l.tax());
                    }
                    invoiceLineRepository.save(line);
                    subtotal = subtotal.add(new BigDecimal(l.amount()));
                    tax = tax.add(l.tax());
                    if (l.timeEntryId() != null) {
                      var entry = timeEntryRepository.findById(l.timeEntryId()).orElseThrow();
                      entry.setInvoiceId(note.getId());
                      timeEntryRepository.save(entry);
                    }
                  }
                  note.recalculateTotals(subtotal, tax.signum() > 0, tax, false);
                  note.approve(
                      "INV-F16-" + UUID.randomUUID().toString().substring(0, 8),
                      memberId,
                      30,
                      today);
                  note.markSent();
                  return invoiceRepository.saveAndFlush(note).getId();
                }));
  }

  private void pay(UUID noteId, BigDecimal amount) {
    runInTenant(
        () ->
            invoiceTransitionService.postPayment(
                noteId, PaymentPosting.manual(amount, PaymentMethod.EFT, "EFT-F16")));
  }

  /** The firm's default VAT rate (falls back to the first positive, non-exempt rate). */
  private TaxRate defaultRate() {
    return taxRateRepository
        .findByIsDefaultTrue()
        .filter(r -> !r.isExempt() && r.getRate() != null && r.getRate().signum() > 0)
        .orElseGet(
            () ->
                taxRateRepository.findAllByOrderBySortOrder().stream()
                    .filter(r -> !r.isExempt() && r.getRate().signum() > 0)
                    .findFirst()
                    .orElseThrow());
  }

  /** VAT at the firm's VAT rate on {@code amount}, rounded to cents. */
  private BigDecimal vatOn(BigDecimal amount) {
    BigDecimal rate = runInTenantReturning(() -> defaultRate().getRate());
    return amount.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
  }

  private Map<String, Object> build(UUID matterId, LocalDate from, LocalDate to) {
    return runInTenantReturning(() -> statementContextBuilder.build(matterId, from, to));
  }

  @SuppressWarnings("unchecked")
  private static BigDecimal summary(Map<String, Object> context, String key) {
    Object value = ((Map<String, Object>) context.get("summary")).get(key);
    assertThat(value).as("summary." + key + " is present").isNotNull();
    return new BigDecimal(value.toString());
  }

  @SuppressWarnings("unchecked")
  private static List<String> disbursementIds(Map<String, Object> context) {
    var entries =
        (List<Map<String, Object>>)
            ((Map<String, Object>) context.get("disbursements")).get("entries");
    return entries.stream().map(e -> String.valueOf(e.get("id"))).toList();
  }

  private String pdfText(UUID generatedDocumentId) {
    String text =
        runInTenantReturning(
            () -> {
              var generated =
                  generatedDocumentRepository.findById(generatedDocumentId).orElseThrow();
              byte[] pdf = storageService.download(generated.getS3Key());
              try (var doc = Loader.loadPDF(pdf)) {
                return new PDFTextStripper().getText(doc);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            });
    return text.replaceAll("[\\s\\u00a0]+", " ");
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .where(
            RequestScopes.CAPABILITIES,
            Set.copyOf(io.b2mash.b2b.b2bstrawman.orgrole.Capability.ALL_NAMES))
        .run(action);
  }

  private <T> T runInTenantReturning(Supplier<T> action) {
    Object[] holder = new Object[1];
    runInTenant(() -> holder[0] = action.get());
    @SuppressWarnings("unchecked")
    T value = (T) holder[0];
    return value;
  }
}

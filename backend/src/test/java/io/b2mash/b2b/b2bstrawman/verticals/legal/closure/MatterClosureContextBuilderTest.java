package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineType;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestIds;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureReason;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Closure-letter arithmetic (Phase 88 622D status set, 626E arithmetic — N-4 / G-D14): over the
 * lines of the matter's ISSUED fee notes (SENT, PARTIALLY_PAID, PAID — never DRAFT, APPROVED or
 * VOID) fees and disbursements are summed excl. VAT, VAT once from the note, so {@code fees + vat +
 * disbursements == Σ note totals}. Values are {@link BigDecimal}s rendered through currency hints.
 */
@ExtendWith(MockitoExtension.class)
class MatterClosureContextBuilderTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();

  @Mock private ProjectRepository projectRepository;
  @Mock private InvoiceRepository invoiceRepository;
  @Mock private InvoiceLineRepository invoiceLineRepository;
  @Mock private Project project;

  private MatterClosureContextBuilder builder;

  @BeforeEach
  void setUp() {
    builder =
        new MatterClosureContextBuilder(
            projectRepository, invoiceRepository, invoiceLineRepository);
    lenient().when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
    lenient().when(project.getClosedAt()).thenReturn(null);
    lenient().when(project.getCreatedAt()).thenReturn(Instant.now());
  }

  /** A mocked fee note whose lines are served by {@code invoiceLineRepository}. */
  private Invoice note(
      InvoiceStatus status, String subtotal, String tax, String total, List<InvoiceLine> lines) {
    Invoice invoice = mock(Invoice.class);
    UUID id = UUID.randomUUID();
    lenient().when(invoice.getId()).thenReturn(id);
    lenient().when(invoice.getStatus()).thenReturn(status);
    lenient().when(invoice.getSubtotal()).thenReturn(new BigDecimal(subtotal));
    lenient().when(invoice.getTaxAmount()).thenReturn(new BigDecimal(tax));
    lenient().when(invoice.getTotal()).thenReturn(new BigDecimal(total));
    lenient().when(invoiceLineRepository.findByInvoiceIdOrderBySortOrder(id)).thenReturn(lines);
    return invoice;
  }

  private static InvoiceLine line(InvoiceLineType type, String amount, String tax) {
    var line =
        new InvoiceLine(
            UUID.randomUUID(), PROJECT_ID, null, "line", BigDecimal.ONE, new BigDecimal(amount), 0);
    line.setLineType(type);
    if (tax != null) {
      TestIds.withField(line, "taxAmount", new BigDecimal(tax));
    }
    return line;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> matter() {
    Map<String, Object> context =
        builder.buildClosureContext(
            PROJECT_ID,
            new ClosureRequest(
                ClosureReason.CONCLUDED, null, false, null, false, null, null, null));
    return (Map<String, Object>) context.get("matter");
  }

  private static BigDecimal money(Map<String, Object> matter, String key) {
    assertThat(matter.get(key))
        .as(key + " is a BigDecimal (formatted by a hint)")
        .isInstanceOf(BigDecimal.class);
    return (BigDecimal) matter.get(key);
  }

  @Test
  void issuedNotesSplitIntoFeesVatAndDisbursements_preIssueAndVoidNotesIgnored() {
    List<Invoice> notes = new ArrayList<>();
    // SENT: fee line 1800 + VAT 270, zero-rated disbursement line 750 → total 2820.
    notes.add(
        note(
            InvoiceStatus.SENT,
            "2550.00",
            "270.00",
            "2820.00",
            List.of(
                line(InvoiceLineType.TIME, "1800.00", "270.00"),
                line(InvoiceLineType.DISBURSEMENT, "750.00", "0.00"))));
    // PARTIALLY_PAID: fixed fee 1000 + VAT 150 → 1150.
    notes.add(
        note(
            InvoiceStatus.PARTIALLY_PAID,
            "1000.00",
            "150.00",
            "1150.00",
            List.of(line(InvoiceLineType.FIXED_FEE, "1000.00", "150.00"))));
    // PAID: tariff 200 + VAT 30, disbursement 100 + VAT 15 → 345.
    notes.add(
        note(
            InvoiceStatus.PAID,
            "300.00",
            "45.00",
            "345.00",
            List.of(
                line(InvoiceLineType.TARIFF, "200.00", "30.00"),
                line(InvoiceLineType.DISBURSEMENT, "100.00", "15.00"))));
    // Not issued → never counted.
    notes.add(
        note(
            InvoiceStatus.DRAFT,
            "9999.00",
            "0.00",
            "9999.00",
            List.of(line(InvoiceLineType.TIME, "9999.00", null))));
    notes.add(
        note(
            InvoiceStatus.APPROVED,
            "8888.00",
            "0.00",
            "8888.00",
            List.of(line(InvoiceLineType.DISBURSEMENT, "8888.00", null))));
    notes.add(
        note(
            InvoiceStatus.VOID,
            "7777.00",
            "0.00",
            "7777.00",
            List.of(line(InvoiceLineType.TIME, "7777.00", null))));
    when(invoiceRepository.findByProjectId(PROJECT_ID)).thenReturn(notes);

    var matter = matter();

    BigDecimal fees = money(matter, "total_fees_billed");
    BigDecimal vat = money(matter, "total_vat");
    BigDecimal disbursements = money(matter, "total_disbursements");
    assertThat(fees).isEqualByComparingTo("3000.00"); // 1800 + 1000 + 200
    assertThat(vat).isEqualByComparingTo("465.00"); // 270 + 150 + 45
    assertThat(disbursements).isEqualByComparingTo("850.00"); // 750 + 100
    // Invariant: nothing double-counted — fees + VAT + disbursements == Σ issued note totals.
    assertThat(fees.add(vat).add(disbursements))
        .isEqualByComparingTo(
            new BigDecimal("2820.00").add(new BigDecimal("1150.00")).add(new BigDecimal("345.00")));
  }

  @Test
  void singleSentNote_matchesTheClosureLetterScenario() {
    List<Invoice> notes =
        List.of(
            note(
                InvoiceStatus.SENT,
                "2550.00",
                "270.00",
                "2820.00",
                List.of(
                    line(InvoiceLineType.TIME, "1800.00", "270.00"),
                    line(InvoiceLineType.DISBURSEMENT, "750.00", "0.00"))));
    when(invoiceRepository.findByProjectId(PROJECT_ID)).thenReturn(notes);

    var matter = matter();

    assertThat(money(matter, "total_fees_billed")).isEqualByComparingTo("1800.00");
    assertThat(money(matter, "total_vat")).isEqualByComparingTo("270.00");
    assertThat(money(matter, "total_disbursements")).isEqualByComparingTo("750.00");
  }

  @Test
  void taxInclusiveNote_feesAndDisbursementsAreExclVat() {
    // Tax-inclusive per-line note: line amounts include VAT and total == subtotal.
    List<Invoice> notes =
        List.of(
            note(
                InvoiceStatus.SENT,
                "1265.00",
                "165.00",
                "1265.00",
                List.of(
                    line(InvoiceLineType.TIME, "1150.00", "150.00"),
                    line(InvoiceLineType.DISBURSEMENT, "115.00", "15.00"))));
    when(invoiceRepository.findByProjectId(PROJECT_ID)).thenReturn(notes);

    var matter = matter();

    BigDecimal fees = money(matter, "total_fees_billed");
    BigDecimal vat = money(matter, "total_vat");
    BigDecimal disbursements = money(matter, "total_disbursements");
    assertThat(fees).isEqualByComparingTo("1000.00");
    assertThat(vat).isEqualByComparingTo("165.00");
    assertThat(disbursements).isEqualByComparingTo("100.00");
    assertThat(fees.add(vat).add(disbursements)).isEqualByComparingTo("1265.00");
  }

  @Test
  void legacyNoteWithoutPerLineTax_vatComesFromTheNote() {
    // Legacy note: manual invoice-level tax, no per-line tax (line taxAmount null), null line type.
    List<Invoice> notes =
        List.of(
            note(
                InvoiceStatus.PAID,
                "2000.00",
                "300.00",
                "2300.00",
                List.of(line(null, "2000.00", null))));
    when(invoiceRepository.findByProjectId(PROJECT_ID)).thenReturn(notes);

    var matter = matter();

    BigDecimal fees = money(matter, "total_fees_billed");
    BigDecimal vat = money(matter, "total_vat");
    BigDecimal disbursements = money(matter, "total_disbursements");
    assertThat(fees).isEqualByComparingTo("2000.00");
    assertThat(vat).isEqualByComparingTo("300.00");
    assertThat(disbursements).isEqualByComparingTo("0");
    assertThat(fees.add(vat).add(disbursements)).isEqualByComparingTo("2300.00");
  }

  @Test
  void noIssuedNotes_allTotalsAreZero() {
    when(invoiceRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());

    var matter = matter();

    assertThat(money(matter, "total_fees_billed")).isEqualByComparingTo("0");
    assertThat(money(matter, "total_vat")).isEqualByComparingTo("0");
    assertThat(money(matter, "total_disbursements")).isEqualByComparingTo("0");
  }

  @Test
  void formatHintsMapTheThreeTotalsToCurrency() {
    assertThat(MatterClosureContextBuilder.formatHints())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "matter.total_fees_billed", "currency",
                "matter.total_vat", "currency",
                "matter.total_disbursements", "currency"));
  }
}

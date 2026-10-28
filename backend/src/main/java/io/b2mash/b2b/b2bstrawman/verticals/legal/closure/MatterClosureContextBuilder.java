package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineType;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureReason;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles the closure-specific rendering context for the {@code matter-closure-letter} Tiptap
 * template (Phase 67, Epic 489B, task 489.15): the {@code closure.*} and {@code matter.*} variable
 * groups referenced by architecture §67.9.1.
 *
 * <p>This class does NOT implement {@code TemplateContextBuilder} — the template is registered with
 * {@code primaryEntityType: PROJECT}, so the auto-dispatched PDF pipeline resolves the base {@code
 * project.*} / {@code customer.*} / {@code org.*} context via {@code ProjectContextBuilder}. The
 * closure letter additionally needs the closure-time variables, which only exist while the {@code
 * ClosureRequest} is in scope; {@code MatterClosureService.generateClosureLetterSafely} builds them
 * here and threads them through {@code GeneratedDocumentService.generateForProject} as extra
 * context, merged over the builder-produced base context before rendering (LZKC-018).
 *
 * <p>The signatory block reads {@code org.signatory.*} from the base org context (Phase 88 626B/D)
 * — nothing signatory-related is emitted here.
 *
 * <p>The {@code matter.*} money totals are {@link BigDecimal}s formatted by the renderer through
 * {@link #formatHints()} (626E): the variable registry does not describe the closure-only {@code
 * matter.*} group, so {@code MatterClosureService} threads these hints next to the extra context.
 */
@Component
public class MatterClosureContextBuilder {

  /**
   * Issued fee notes — exactly SENT, PARTIALLY_PAID and PAID (Phase 88 622D status set); DRAFT,
   * APPROVED and VOID notes are never counted.
   */
  private static final Set<InvoiceStatus> BILLED_INVOICE_STATUSES =
      Set.of(InvoiceStatus.SENT, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID);

  /** Currency hints for the {@code matter.*} totals, merged over the registry hints at render. */
  static final Map<String, String> FORMAT_HINTS =
      Map.of(
          "matter.total_fees_billed", "currency",
          "matter.total_vat", "currency",
          "matter.total_disbursements", "currency");

  private final ProjectRepository projectRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;

  public MatterClosureContextBuilder(
      ProjectRepository projectRepository,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository) {
    this.projectRepository = projectRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
  }

  /** Format hints for the {@code matter.*} totals — merged over the registry hints at render. */
  static Map<String, String> formatHints() {
    return FORMAT_HINTS;
  }

  /**
   * Returns a map with {@code closure.*} and {@code matter.*} keys suitable for merging over the
   * {@code ProjectContextBuilder}-produced base context for Tiptap variable substitution. {@code
   * closure.date} is an ISO date string; {@code matter.total_fees_billed}, {@code total_vat} and
   * {@code total_disbursements} are {@link BigDecimal}s the renderer formats as currency through
   * {@link #formatHints()} (626E) — fees and disbursements excl. VAT, VAT once, so the three add up
   * to the issued fee notes' totals.
   *
   * <p>{@code @Transactional(readOnly = true)} so Hibernate lazy associations resolve within a
   * session — Spring Boot 4 disables OSIV by default, so the context assembly must run inside a
   * transaction to avoid {@code LazyInitializationException} (H5). When invoked from {@code
   * generateClosureLetterSafely}'s {@code REQUIRES_NEW} transaction it simply participates.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> buildClosureContext(UUID projectId, ClosureRequest req) {
    var project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    Map<String, Object> context = new HashMap<>();

    // closure.*
    // Note: `reason` stays as the raw enum token (kept for integrations/analytics that filter by
    // enum); `reason_label` is the user-facing display string rendered in the closure letter.
    // CR-Minor-2: the legal-za closure-letter template binds to closure.reason_label.
    // closure.date is the PERSISTED closure timestamp (project.closedAt, stamped by
    // Project#closeMatter inside performClose), not render-time "now" — a letter re-rendered
    // later (or a render straddling midnight UTC) must still carry the real closure date. The
    // now() fallback only covers the not-yet-closed edge (e.g. a preview before the close
    // transaction has stamped closedAt).
    Instant closedAt = project.getClosedAt();
    LocalDate closureDate =
        closedAt != null
            ? closedAt.atZone(ZoneOffset.UTC).toLocalDate()
            : LocalDate.now(ZoneOffset.UTC);

    var closureMap = new LinkedHashMap<String, Object>();
    closureMap.put("reason", req.reason().name());
    closureMap.put("reason_label", reasonLabel(req.reason()));
    closureMap.put("date", closureDate.toString());
    closureMap.put("notes", req.notes() != null ? req.notes() : "");
    context.put("closure", closureMap);

    // matter.*
    var matterMap = new LinkedHashMap<String, Object>();
    BilledTotals totals = billedTotals(projectId);
    matterMap.put("total_fees_billed", totals.fees());
    matterMap.put("total_vat", totals.vat());
    matterMap.put("total_disbursements", totals.disbursements());
    matterMap.put("duration_months", durationMonths(project.getCreatedAt()));
    context.put("matter", matterMap);

    return context;
  }

  /**
   * Fees and disbursements excl. VAT summed from the lines of the matter's issued fee notes; VAT
   * once, from each note (covers legacy notes with a manual invoice-level tax and no per-line tax).
   * A fee note is "on the matter" when any of its lines carries the project ({@code
   * InvoiceRepository.findByProjectId}); all its lines count. Disbursements are counted only as
   * DISBURSEMENT lines of issued notes — never again from the disbursement register (N-4: they used
   * to be added on top of the VAT-inclusive note totals, and disbursements sitting on DRAFT notes
   * were counted because they are marked billed at draft time).
   */
  private BilledTotals billedTotals(UUID projectId) {
    BigDecimal fees = BigDecimal.ZERO;
    BigDecimal vat = BigDecimal.ZERO;
    BigDecimal disbursements = BigDecimal.ZERO;
    for (Invoice note : invoiceRepository.findByProjectId(projectId)) {
      if (!BILLED_INVOICE_STATUSES.contains(note.getStatus())) {
        continue;
      }
      BigDecimal noteVat = nz(note.getTaxAmount());
      // Tax-inclusive per-line notes carry VAT inside each line amount (Invoice.recalculateTotals
      // sets total == subtotal only for them); the invoice row has no tax-inclusive flag.
      boolean inclusive =
          noteVat.signum() > 0 && nz(note.getTotal()).compareTo(nz(note.getSubtotal())) == 0;
      vat = vat.add(noteVat);
      for (InvoiceLine line : invoiceLineRepository.findByInvoiceIdOrderBySortOrder(note.getId())) {
        BigDecimal exclVat =
            nz(line.getAmount()).subtract(inclusive ? nz(line.getTaxAmount()) : BigDecimal.ZERO);
        if (line.getLineType() == InvoiceLineType.DISBURSEMENT) {
          disbursements = disbursements.add(exclVat);
        } else {
          fees = fees.add(exclVat); // null line type (legacy rows) counts as a fee line
        }
      }
    }
    return new BilledTotals(fees, vat, disbursements);
  }

  private record BilledTotals(BigDecimal fees, BigDecimal vat, BigDecimal disbursements) {}

  private static BigDecimal nz(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  private long durationMonths(Instant createdAt) {
    if (createdAt == null) {
      return 0;
    }
    LocalDate start = createdAt.atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate end = LocalDate.now(ZoneOffset.UTC);
    // Defensive: if createdAt is somehow in the future (clock skew / data corruption), clamp to 0
    // rather than render a negative duration in the closure letter (CR nitpick).
    if (start.isAfter(end)) {
      return 0;
    }
    Period between = Period.between(start, end);
    return (long) between.getYears() * 12L + between.getMonths();
  }

  /**
   * Maps a {@link ClosureReason} enum to a user-facing label for the closure letter. Intentionally
   * lightweight — a richer i18n story can replace this when we introduce per-locale rendering.
   */
  private String reasonLabel(ClosureReason reason) {
    return switch (reason) {
      case CONCLUDED -> "Matter concluded";
      case CLIENT_TERMINATED -> "Client terminated engagement";
      case REFERRED_OUT -> "Referred out";
      case OTHER -> "Other";
    };
  }
}

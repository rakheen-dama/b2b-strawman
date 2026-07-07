package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceStatus;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureReason;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementApprovalStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementBillingStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.LegalDisbursement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>{@code org.principal_attorney} (referenced by the template) has no backing OrgSettings field
 * yet and intentionally stays blank — it is not emitted here.
 */
@Component
public class MatterClosureContextBuilder {

  private static final Set<InvoiceStatus> BILLED_INVOICE_STATUSES =
      Set.of(InvoiceStatus.SENT, InvoiceStatus.PAID);

  private final ProjectRepository projectRepository;
  private final InvoiceRepository invoiceRepository;
  private final DisbursementRepository disbursementRepository;

  public MatterClosureContextBuilder(
      ProjectRepository projectRepository,
      InvoiceRepository invoiceRepository,
      DisbursementRepository disbursementRepository) {
    this.projectRepository = projectRepository;
    this.invoiceRepository = invoiceRepository;
    this.disbursementRepository = disbursementRepository;
  }

  /**
   * Returns a map with {@code closure.*} and {@code matter.*} keys suitable for merging over the
   * {@code ProjectContextBuilder}-produced base context for Tiptap variable substitution. Values
   * are pre-formatted (ISO date, plain decimal strings) — these keys carry no format hints in
   * {@code VariableMetadataRegistry}, so the renderer emits them verbatim.
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
    matterMap.put("total_fees_billed", totalFeesBilled(projectId).toPlainString());
    matterMap.put("total_disbursements", totalDisbursementsBilled(projectId).toPlainString());
    matterMap.put("duration_months", durationMonths(project.getCreatedAt()));
    context.put("matter", matterMap);

    return context;
  }

  private BigDecimal totalFeesBilled(UUID projectId) {
    List<Invoice> invoices = invoiceRepository.findByProjectId(projectId);
    return invoices.stream()
        .filter(i -> BILLED_INVOICE_STATUSES.contains(i.getStatus()))
        .map(Invoice::getTotal)
        .filter(t -> t != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal totalDisbursementsBilled(UUID projectId) {
    List<LegalDisbursement> billed =
        disbursementRepository.findByProjectIdAndApprovalStatusAndBillingStatus(
            projectId,
            DisbursementApprovalStatus.APPROVED.name(),
            DisbursementBillingStatus.BILLED.name());
    return billed.stream()
        .map(
            d -> {
              BigDecimal amt = d.getAmount() != null ? d.getAmount() : BigDecimal.ZERO;
              BigDecimal vat = d.getVatAmount() != null ? d.getVatAmount() : BigDecimal.ZERO;
              return amt.add(vat);
            })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
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

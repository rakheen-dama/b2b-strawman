package io.b2mash.b2b.b2bstrawman.verticals.legal.closure;

import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.MatterFeeNoteTotals;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureReason;
import io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto.ClosureRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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

  /** Currency hints for the {@code matter.*} totals, merged over the registry hints at render. */
  static final Map<String, String> FORMAT_HINTS =
      Map.of(
          "matter.total_fees_billed", "currency",
          "matter.total_vat", "currency",
          "matter.total_disbursements", "currency",
          "matter.balance_owing", "currency");

  private final ProjectRepository projectRepository;
  private final MatterFeeNoteTotals feeNoteTotals;
  private final Clock clock;

  public MatterClosureContextBuilder(
      ProjectRepository projectRepository, MatterFeeNoteTotals feeNoteTotals, Clock clock) {
    this.projectRepository = projectRepository;
    this.feeNoteTotals = feeNoteTotals;
    this.clock = clock;
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
            .orElseThrow(() -> new ResourceNotFoundException(EntityNoun.MATTER, projectId));

    Map<String, Object> context = new HashMap<>();

    // closure.*
    // Note: `reason` stays as the raw enum token (kept for integrations/analytics that filter by
    // enum); `reason_label` is the user-facing display string rendered in the closure letter.
    // CR-Minor-2: the legal-za closure-letter template binds to closure.reason_label.
    // closure.date is the PERSISTED closure timestamp (project.closedAt, stamped by
    // Project#closeMatter inside performClose), not render-time "now" — a letter re-rendered
    // later (or a render straddling midnight SAST) must still carry the real closure date. The
    // now() fallback only covers the not-yet-closed edge (e.g. a preview before the close
    // transaction has stamped closedAt).
    Instant closedAt = project.getClosedAt();
    LocalDate closureDate =
        closedAt != null ? closedAt.atZone(KaziTime.ZONE).toLocalDate() : LocalDate.now(clock);

    var closureMap = new LinkedHashMap<String, Object>();
    closureMap.put("reason", req.reason().name());
    closureMap.put("reason_label", reasonLabel(req.reason()));
    closureMap.put("date", closureDate.toString());
    closureMap.put("notes", req.notes() != null ? req.notes() : "");
    context.put("closure", closureMap);

    // matter.*
    // matter.* — fees and disbursements excl. VAT from the lines of the matter's ISSUED fee notes,
    // VAT once per note, and what is still owing on them: the same fee-note basis the Statement of
    // Account summary reads (MatterFeeNoteTotals, G89-F16 / D9). Disbursements are counted only as
    // DISBURSEMENT lines of issued notes — never again from the disbursement register (N-4).
    var matterMap = new LinkedHashMap<String, Object>();
    var totals = feeNoteTotals.current(projectId);
    matterMap.put("total_fees_billed", totals.feesExclVat());
    matterMap.put("total_vat", totals.vat());
    matterMap.put("total_disbursements", totals.disbursementsExclVat());
    matterMap.put("balance_owing", totals.outstanding());
    matterMap.put("duration_months", durationMonths(project.getCreatedAt()));
    context.put("matter", matterMap);

    return context;
  }

  private long durationMonths(Instant createdAt) {
    if (createdAt == null) {
      return 0;
    }
    LocalDate start = createdAt.atZone(KaziTime.ZONE).toLocalDate();
    LocalDate end = LocalDate.now(clock);
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

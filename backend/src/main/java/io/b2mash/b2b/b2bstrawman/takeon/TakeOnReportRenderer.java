package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.config.KaziTime;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportSnapshot.CreditorLineEntry;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportSnapshot.CreditorListSection;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportSnapshot.InvestmentLine;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportSnapshot.ReconciledPosition;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionEquation.TrustEquationSummary;
import io.b2mash.b2b.b2bstrawman.template.PdfRenderingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders a take-on report's PDF and CSV from its immutable snapshot (Phase 93 657D, §17.3.12,
 * §17.2.7, ADR-391, FD-15) and stores them in S3 under {@link TakeOnStorageKeys#reportKey} with a
 * {@code take_on_report_files} row (SHA-256, size). Called by the {@code take_on_drain} job after
 * every run's batches ({@link #renderPending}): a file lost to a crash is rendered again from the
 * same snapshot; an existing file is never overwritten.
 *
 * <p>Both renders are pure functions of the stored report ({@link #renderPdf}, {@link #renderCsv}):
 * every amount and date is formatted in Java from the snapshot, and the PDF's dates and trailer id
 * come from {@code generated_at} — never "now" — so a re-render is byte-identical.
 *
 * <p>Each format is rendered in its own transaction under the report's render claim ({@code
 * take_on_report_renders} {@code FOR UPDATE SKIP LOCKED}: two drains never render one report at
 * once), so a CSV failure keeps the PDF. A failed run counts one attempt in a transaction of its
 * own; the {@value TakeOnReportRender#MAX_ATTEMPTS}th sets {@code failed_at} and logs an ERROR, and
 * the drain and the sweep then leave the report alone until "Try again" (R-18).
 */
@Component
public class TakeOnReportRenderer {

  private static final Logger log = LoggerFactory.getLogger(TakeOnReportRenderer.class);

  static final String TEMPLATE = "takeon/take-on-report";

  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH).withZone(KaziTime.ZONE);

  private final TakeOnReportRepository reports;
  private final TakeOnReportFileRepository files;
  private final TakeOnReportRenderRepository renders;
  private final StorageService storage;
  private final TemplateEngine templateEngine;
  private final PdfRenderingService pdfRenderingService;
  private final TransactionTemplate tx;
  private final TransactionTemplate failureTx;
  private final Clock clock;

  public TakeOnReportRenderer(
      TakeOnReportRepository reports,
      TakeOnReportFileRepository files,
      TakeOnReportRenderRepository renders,
      StorageService storage,
      TemplateEngine templateEngine,
      PdfRenderingService pdfRenderingService,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.reports = reports;
    this.files = files;
    this.renders = renders;
    this.storage = storage;
    this.templateEngine = templateEngine;
    this.pdfRenderingService = pdfRenderingService;
    this.tx = new TransactionTemplate(transactionManager);
    this.failureTx = new TransactionTemplate(transactionManager);
    this.failureTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  /**
   * Renders the files of every report of the bound tenant missing one and not given up, oldest
   * first, until {@code deadline}. Never throws: a failure is counted and logged per report.
   */
  public void renderPending(Instant deadline) {
    List<UUID> pending = tx.execute(status -> reports.findIdsNeedingRender());
    if (pending == null) {
      return;
    }
    for (UUID reportId : pending) {
      if (!clock.instant().isBefore(deadline)) {
        return;
      }
      renderOne(reportId);
    }
  }

  /**
   * Renders the missing files of one report (each format in its own transaction). Returns {@code
   * true} when no format failed. A failed run records one attempt.
   */
  public boolean renderOne(UUID reportId) {
    RuntimeException failure = null;
    for (var format : TakeOnReportFile.Format.values()) {
      try {
        tx.executeWithoutResult(status -> renderFormat(reportId, format));
      } catch (RuntimeException e) {
        log.warn(
            "take-on report render failed report={} format={} ({})",
            reportId,
            format,
            e.getClass().getSimpleName());
        if (failure == null) {
          failure = e;
        }
      }
    }
    if (failure != null) {
      recordFailure(reportId, failure);
      return false;
    }
    return true;
  }

  private void renderFormat(UUID reportId, TakeOnReportFile.Format format) {
    renders.ensureExists(reportId);
    var claim = renders.claimSkipLocked(reportId);
    if (claim.isEmpty() || claim.get().isFailed()) {
      return; // another drain is rendering it, or it was given up
    }
    if (files.existsByReportIdAndFormat(reportId, format)) {
      return; // never overwritten
    }
    var report = reports.findById(reportId).orElse(null);
    if (report == null) {
      return;
    }
    byte[] bytes = format == TakeOnReportFile.Format.PDF ? renderPdf(report) : renderCsv(report);
    String key =
        TakeOnStorageKeys.reportKey(
            RequestScopes.requireTenantId(), report.getBatchId(), reportId, format.extension());
    storage.upload(key, bytes, format.contentType());
    files.saveAndFlush(
        new TakeOnReportFile(reportId, format, key, sha256(bytes), bytes.length, clock.instant()));
  }

  private void recordFailure(UUID reportId, RuntimeException failure) {
    String code = failure.getClass().getSimpleName();
    try {
      failureTx.executeWithoutResult(
          status -> {
            renders.ensureExists(reportId);
            var render = renders.findById(reportId).orElseThrow();
            boolean gaveUp = render.recordFailure(code, clock.instant());
            renders.saveAndFlush(render);
            if (gaveUp) {
              log.error(
                  "take-on report render failed permanently report={} code={}", reportId, code);
            }
          });
    } catch (RuntimeException e) {
      log.error(
          "take-on report render failure NOT recorded report={} code={} ({})",
          reportId,
          code,
          e.getClass().getSimpleName());
    }
  }

  /** SHA-256, 64 lower-case hex. */
  static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  // --- the PDF -----------------------------------------------------------------------------

  /** The PDF of {@code report}: the classpath template, byte-stable from {@code generated_at}. */
  public byte[] renderPdf(TakeOnReport report) {
    var ctx = new Context(Locale.ENGLISH);
    ctx.setVariable("r", view(report));
    String html = templateEngine.process(TEMPLATE, ctx);
    return pdfRenderingService.htmlToPdf(html, report.getGeneratedAt());
  }

  /** The template's view of a report: every figure and date already in words (no raw enum). */
  public record ReportView(
      String firmName,
      String accountName,
      String accountNumber,
      String bankName,
      String cutoverDate,
      boolean midMonth,
      List<PositionRow> position,
      List<CreditorSectionView> creditorSections,
      List<InvestmentView> section864,
      List<InvestmentView> section863,
      List<UnidentifiedView> unidentified,
      List<OutstandingView> outstanding,
      List<LabelValue> controlTotals,
      List<String> flags,
      SignOffView signOff,
      List<SourceView> sources,
      String reportId,
      String snapshotSha256,
      String generatedAt) {}

  public record PositionRow(String label, String previous, String kazi, String difference) {}

  public record CreditorSectionView(
      String accountName,
      String asAt,
      String cashbook,
      String creditorTotal,
      List<GroupView> groups) {}

  public record GroupView(String heading, List<LineView> lines) {}

  public record LineView(String name, String detail, String amount) {}

  public record InvestmentView(
      String client,
      String matter,
      String heldIn,
      String institution,
      String principal,
      String rate,
      String placed,
      String accruedInterest,
      String perConfirmation,
      String instruction) {}

  public record UnidentifiedView(
      String receivedDate, String age, String amount, String reference) {}

  public record OutstandingView(
      String kind, String date, String reference, String client, String matter, String amount) {}

  public record LabelValue(String label, String value) {}

  public record SignOffView(
      String signedBy,
      String role,
      String statementVersion,
      String statementText,
      String signedAt,
      String preparedBy,
      List<String> notes) {}

  public record SourceView(
      String fileName,
      String section,
      String sheetName,
      String sha256,
      String rows,
      String uploadedBy,
      String uploadedAt) {}

  /** The template's view of {@code report} (public for tests). */
  public ReportView view(TakeOnReport report) {
    var s = TakeOnReportSnapshot.from(report.getSnapshot());
    var account = s.account();
    var investments = s.investments() == null ? List.<InvestmentLine>of() : s.investments();
    var sections = new ArrayList<CreditorSectionView>();
    if (s.creditorList() != null) {
      sections.add(creditorSection(s.creditorList()));
    }
    if (s.investmentAccounts() != null) {
      s.investmentAccounts().stream()
          .map(TakeOnReportRenderer::creditorSection)
          .forEach(sections::add);
    }
    var sign = s.signOff();
    return new ReportView(
        s.firm() == null ? null : s.firm().name(),
        account == null ? null : account.name(),
        account == null ? null : account.accountNumber(),
        account == null ? null : account.bankName(),
        longDate(s.cutoverDate()),
        s.midMonth(),
        position(s.previousSystem(), s.kazi(), s.differences()),
        List.copyOf(sections),
        investments.stream()
            .filter(i -> "CLIENT_INSTRUCTION".equals(i.basis()))
            .map(TakeOnReportRenderer::investment)
            .toList(),
        investments.stream()
            .filter(i -> !"CLIENT_INSTRUCTION".equals(i.basis()))
            .map(TakeOnReportRenderer::investment)
            .toList(),
        s.unidentified() == null
            ? List.of()
            : s.unidentified().stream()
                .map(
                    u ->
                        new UnidentifiedView(
                            longDate(u.receivedDate()),
                            u.ageInDaysAtCutover() + " days",
                            rand(u.amount()),
                            u.reference()))
                .toList(),
        s.outstandingItems() == null
            ? List.of()
            : s.outstandingItems().stream()
                .map(
                    o ->
                        new OutstandingView(
                            outstandingKind(o.kind()),
                            longDate(o.originalDate()),
                            o.reference(),
                            o.clientName(),
                            o.matterName(),
                            rand(o.amount())))
                .toList(),
        controlTotals(s.totals()),
        s.flags() == null ? List.of() : s.flags().stream().map(TakeOnReportRenderer::flag).toList(),
        sign == null
            ? null
            : new SignOffView(
                sign.signedBy(),
                sign.role(),
                sign.statementVersion(),
                sign.statementText(),
                dateTime(sign.signedAt()),
                sign.preparedBy(),
                sign.notes() == null ? List.of() : sign.notes()),
        s.sources() == null
            ? List.of()
            : s.sources().stream()
                .map(
                    f ->
                        new SourceView(
                            f.fileName(),
                            sectionLabel(f.section()),
                            f.sheetName(),
                            f.sha256(),
                            Integer.toString(f.rowCount()),
                            f.uploadedBy(),
                            dateTime(f.uploadedAt())))
                .toList(),
        report.getId().toString(),
        report.getSnapshotSha256(),
        DATE_TIME.format(report.getGeneratedAt()));
  }

  private static List<PositionRow> position(
      ReconciledPosition previous, ReconciledPosition kazi, ReconciledPosition difference) {
    var rows = new ArrayList<PositionRow>();
    rows.add(row("Bank balance", previous, kazi, difference, ReconciledPosition::bankBalance));
    rows.add(
        row(
            "Outstanding deposits",
            previous,
            kazi,
            difference,
            ReconciledPosition::outstandingDeposits));
    rows.add(
        row(
            "Outstanding payments",
            previous,
            kazi,
            difference,
            ReconciledPosition::outstandingPayments));
    rows.add(
        row("Adjusted bank balance", previous, kazi, difference, ReconciledPosition::adjustedBank));
    rows.add(row("Cashbook", previous, kazi, difference, ReconciledPosition::cashbook));
    rows.add(
        row(
            "Legal Practitioners' Fidelity Fund (the Fund) interest and charges ledger",
            previous,
            kazi,
            difference,
            ReconciledPosition::fundLedger));
    rows.add(row("Total invested", previous, kazi, difference, ReconciledPosition::investedTotal));
    return List.copyOf(rows);
  }

  private static PositionRow row(
      String label,
      ReconciledPosition previous,
      ReconciledPosition kazi,
      ReconciledPosition difference,
      Function<ReconciledPosition, String> figure) {
    return new PositionRow(
        label,
        previous == null ? "—" : rand(figure.apply(previous)),
        kazi == null ? "—" : rand(figure.apply(kazi)),
        difference == null ? "—" : rand(figure.apply(difference)));
  }

  private static CreditorSectionView creditorSection(CreditorListSection section) {
    var lines = section.lines() == null ? List.<CreditorLineEntry>of() : section.lines();
    var groups = new ArrayList<GroupView>();
    for (var kind :
        List.of("CLIENT", "CLIENT_INVESTMENT", "FUND", "UNIDENTIFIED", "INTER_ACCOUNT")) {
      var of =
          lines.stream()
              .filter(l -> kind.equals(l.kind()))
              .map(TakeOnReportRenderer::creditorLine)
              .toList();
      if (!of.isEmpty()) {
        groups.add(new GroupView(groupHeading(kind), of));
      }
    }
    return new CreditorSectionView(
        section.accountName(),
        longDate(section.asAt()),
        rand(section.cashbook()),
        rand(section.creditorTotal()),
        List.copyOf(groups));
  }

  private static LineView creditorLine(CreditorLineEntry line) {
    String detail;
    if ("UNIDENTIFIED".equals(line.kind())) {
      detail =
          "Received "
              + longDate(line.receivedDate())
              + (line.bankDescription() == null ? "" : " · " + line.bankDescription());
    } else if (line.matters() != null && !line.matters().isEmpty()) {
      detail =
          line.matters().stream()
              .map(m -> (m.name() == null ? "No matter" : m.name()) + " " + rand(m.balance()))
              .collect(Collectors.joining("; "));
    } else {
      detail = "";
    }
    return new LineView(line.name(), detail, rand(line.balance()));
  }

  private static InvestmentView investment(InvestmentLine i) {
    return new InvestmentView(
        i.clientName(),
        i.matterName(),
        i.investmentAccountName(),
        join(i.institution(), i.accountNumber()),
        rand(i.principal()),
        i.interestRatePercent() == null ? "" : i.interestRatePercent() + "%",
        longDate(i.depositDate()),
        rand(i.accruedInterest()),
        rand(i.balancePerConfirmation()),
        join(i.instructionReference(), longDate(i.instructionDate())));
  }

  private static List<LabelValue> controlTotals(TrustEquationSummary totals) {
    if (totals == null) {
      return List.of();
    }
    var rows = new ArrayList<LabelValue>();
    if (totals.eq1() != null) {
      var e = totals.eq1();
      rows.add(
          new LabelValue(
              "Bank balance + outstanding deposits − outstanding payments = cashbook",
              rand(e.bankBalance())
                  + " + "
                  + rand(e.plusOutstandingDeposits())
                  + " − "
                  + rand(e.lessOutstandingPayments())
                  + " = "
                  + rand(e.adjustedBank())
                  + " against "
                  + rand(e.cashbook())
                  + holds(e.holds())));
    }
    if (totals.itemsTie() != null) {
      var t = totals.itemsTie();
      rows.add(
          new LabelValue(
              "Outstanding items listed agree with the reconciled position",
              "Deposits "
                  + rand(t.listedDeposits())
                  + ", payments "
                  + rand(t.listedPayments())
                  + holds(t.holds())));
    }
    if (totals.eq2() != null) {
      var e = totals.eq2();
      rows.add(
          new LabelValue(
              "Cashbook = trust creditors",
              rand(e.cashbook()) + " against " + rand(e.creditors()) + holds(e.holds())));
    }
    if (totals.investments() != null) {
      var t = totals.investments();
      rows.add(
          new LabelValue(
              "Investments listed agree with the institutions' confirmations",
              rand(t.listed()) + " against " + rand(t.perConfirmations()) + holds(t.holds())));
    }
    if (totals.deficit() != null) {
      var d = totals.deficit();
      rows.add(
          new LabelValue(
              "Trust money held covers the trust creditors",
              rand(d.moneyHeld())
                  + " against "
                  + rand(d.trustCreditors())
                  + (d.inDeficit() ? " — in deficit" : " — no deficit")));
    }
    return List.copyOf(rows);
  }

  private static String holds(boolean holds) {
    return holds ? " — agrees" : " — does not agree";
  }

  // --- the CSV -----------------------------------------------------------------------------

  /**
   * The CSV of {@code report} (§17.3.12): a title line and a generated line from the report, the
   * header {@code Section,Group,Client,Matter,Reference,Date,Amount}, one line per creditor line of
   * each account's list, then the investments, unidentified deposits and items outstanding at
   * cutover. Amounts are the snapshot's two-decimal strings; cells are formula-guarded.
   */
  public byte[] renderCsv(TakeOnReport report) {
    var s = TakeOnReportSnapshot.from(report.getSnapshot());
    var out = new StringBuilder();
    String accountName = s.account() == null ? "" : s.account().name();
    out.append("# Trust take-on report — ")
        .append(TakeOnResultService.csv(accountName))
        .append(" at ")
        .append(longDate(s.cutoverDate()))
        .append('\n');
    out.append("# Generated ").append(report.getGeneratedAt().toString()).append('\n');
    out.append("Section,Group,Client,Matter,Reference,Date,Amount\n");
    var lists = new ArrayList<CreditorListSection>();
    if (s.creditorList() != null) {
      lists.add(s.creditorList());
    }
    if (s.investmentAccounts() != null) {
      lists.addAll(s.investmentAccounts());
    }
    for (var list : lists) {
      for (var line : list.lines() == null ? List.<CreditorLineEntry>of() : list.lines()) {
        boolean unidentified = "UNIDENTIFIED".equals(line.kind());
        csvLine(
            out,
            "CREDITOR",
            groupHeading(line.kind()),
            line.name(),
            line.matters() == null
                ? null
                : line.matters().stream()
                    .map(m -> m.name())
                    .filter(n -> n != null)
                    .collect(Collectors.joining("; ")),
            unidentified ? line.bankDescription() : list.accountName(),
            unidentified ? line.receivedDate() : list.asAt(),
            line.balance());
      }
    }
    for (var i : s.investments() == null ? List.<InvestmentLine>of() : s.investments()) {
      csvLine(
          out,
          "INVESTMENT",
          "CLIENT_INSTRUCTION".equals(i.basis())
              ? "Section 86(4) investments"
              : "Section 86(3) investments",
          i.clientName(),
          i.matterName(),
          join(i.institution(), i.accountNumber()),
          i.depositDate(),
          i.principal());
    }
    for (var u :
        s.unidentified() == null
            ? List.<TakeOnReportSnapshot.UnidentifiedLine>of()
            : s.unidentified()) {
      csvLine(
          out,
          "UNIDENTIFIED",
          "Unidentified deposits",
          null,
          null,
          u.reference(),
          u.receivedDate(),
          u.amount());
    }
    for (var o :
        s.outstandingItems() == null
            ? List.<TakeOnReportSnapshot.OutstandingLine>of()
            : s.outstandingItems()) {
      csvLine(
          out,
          "OUTSTANDING",
          outstandingKind(o.kind()),
          o.clientName(),
          o.matterName(),
          o.reference(),
          o.originalDate(),
          o.amount());
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static void csvLine(StringBuilder out, String... cells) {
    for (int i = 0; i < cells.length; i++) {
      if (i > 0) {
        out.append(',');
      }
      out.append(TakeOnResultService.csv(cells[i]));
    }
    out.append('\n');
  }

  // --- words -------------------------------------------------------------------------------

  /** The X3 heading of a creditor line's kind. */
  static String groupHeading(String kind) {
    return switch (kind == null ? "" : kind) {
      case "CLIENT" -> "Client ledgers";
      case "CLIENT_INVESTMENT" -> "Section 86(4) investments";
      case "FUND" -> "Fund interest and charges ledger";
      case "UNIDENTIFIED" -> "Unidentified deposits";
      case "INTER_ACCOUNT" -> "Section 86(3) investments";
      default -> "Other trust creditors";
    };
  }

  private static final Map<String, String> FLAG_WORDS =
      Map.of(
          TakeOnReportSnapshot.MID_MONTH_CUTOVER,
          "Mid-month cutover",
          "UNIDENTIFIED_OVER_ONE_YEAR",
          "Unidentified deposit older than one year",
          "FUND_LEDGER_IN_DEBIT",
          "Legal Practitioners' Fidelity Fund ledger in debit");

  static String flag(String code) {
    return FLAG_WORDS.getOrDefault(code, "Flagged");
  }

  static String outstandingKind(String kind) {
    return switch (kind == null ? "" : kind) {
      case "DEPOSIT_IN_TRANSIT" -> "Deposit in transit";
      case "UNPRESENTED_PAYMENT" -> "Unpresented payment";
      case "INVESTMENT_IN_TRANSIT" -> "Investment movement in transit";
      default -> "Item outstanding at cutover";
    };
  }

  private static String sectionLabel(String section) {
    if (section == null) {
      return "";
    }
    try {
      return TakeOnSection.valueOf(section).label();
    } catch (IllegalArgumentException e) {
      return "File";
    }
  }

  private static String rand(String amount) {
    return amount == null || amount.isBlank()
        ? "—"
        : IntegrityMessages.rand(new BigDecimal(amount));
  }

  private static String longDate(String isoDate) {
    return isoDate == null || isoDate.isBlank()
        ? ""
        : IntegrityMessages.date(LocalDate.parse(isoDate));
  }

  private static String dateTime(String isoInstant) {
    return isoInstant == null || isoInstant.isBlank()
        ? ""
        : DATE_TIME.format(Instant.parse(isoInstant));
  }

  private static String join(String a, String b) {
    if (a == null || a.isBlank()) {
      return b == null ? "" : b;
    }
    return b == null || b.isBlank() ? a : a + " · " + b;
  }
}

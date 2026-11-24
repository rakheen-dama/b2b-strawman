package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportSnapshot.AccountHeader;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportSnapshot.FirmHeader;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportSnapshot.InvestmentLine;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportSnapshot.OutstandingLine;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportSnapshot.ReconciledPosition;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportSnapshot.SignOffSection;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportSnapshot.SourceFile;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportSnapshot.UnidentifiedLine;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionEquation.TrustPositionSummary;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccount;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningPlan;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.TrustOpeningSelfCheck;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The take-on report (Phase 93 657C, §17.3.12, ADR-391, FD-15): written once by the trust commit
 * ({@link #snapshot}, inside its transaction, after the self-check, so it describes exactly the
 * position posted), and read as JSON by {@code TakeOnReportController} ({@code VIEW_TRUST}, the
 * gate of the monthly lists — never {@code ReportingController}, P-12). Audits {@code
 * takeon.report.generated}. The PDF and CSV are 657D's.
 */
@Service
public class TakeOnReportService {

  /** One report in a list (the results page, the account's take-on reports). */
  public record TakeOnReportSummaryResponse(
      UUID id,
      UUID batchId,
      UUID trustAccountId,
      LocalDate cutoverDate,
      Instant generatedAt,
      String generatedByName) {}

  /** The report as JSON: the stored snapshot ({@link TakeOnReportSnapshot}) and its SHA-256. */
  public record TakeOnReportResponse(
      UUID id,
      UUID batchId,
      UUID trustAccountId,
      LocalDate cutoverDate,
      String snapshotSha256,
      Instant generatedAt,
      Map<String, Object> snapshot) {}

  private final TakeOnReportRepository reports;
  private final TakeOnSourceRepository sources;
  private final TrustAccountRepository trustAccounts;
  private final TrustMonthlyListService monthlyLists;
  private final OrganizationRepository organizations;
  private final MemberNameResolver memberNames;
  private final TakeOnAudit audit;
  private final Clock clock;

  public TakeOnReportService(
      TakeOnReportRepository reports,
      TakeOnSourceRepository sources,
      TrustAccountRepository trustAccounts,
      TrustMonthlyListService monthlyLists,
      OrganizationRepository organizations,
      MemberNameResolver memberNames,
      TakeOnAudit audit,
      Clock clock) {
    this.reports = reports;
    this.sources = sources;
    this.trustAccounts = trustAccounts;
    this.monthlyLists = monthlyLists;
    this.organizations = organizations;
    this.memberNames = memberNames;
    this.audit = audit;
    this.clock = clock;
  }

  // --- reads (VIEW_TRUST on the controller) ---

  /** {@code GET /api/take-on/reports?trustAccountId=}: newest cutover first; all when null. */
  @Transactional(readOnly = true)
  public List<TakeOnReportSummaryResponse> list(UUID trustAccountId) {
    var found =
        trustAccountId == null
            ? reports.findAllByOrderByGeneratedAtDesc()
            : reports.findByTrustAccountIdOrderByCutoverDateDesc(trustAccountId);
    var names = memberNames.resolveNames(found.stream().map(TakeOnReport::getGeneratedBy).toList());
    return found.stream()
        .map(
            r ->
                new TakeOnReportSummaryResponse(
                    r.getId(),
                    r.getBatchId(),
                    r.getTrustAccountId(),
                    r.getCutoverDate(),
                    r.getGeneratedAt(),
                    names.get(r.getGeneratedBy())))
        .toList();
  }

  /** {@code GET /api/take-on/reports/{reportId}}: the snapshot as stored. */
  @Transactional(readOnly = true)
  public TakeOnReportResponse get(UUID reportId) {
    var report =
        reports
            .findById(reportId)
            .orElseThrow(() -> new ResourceNotFoundException("Take-on report", reportId));
    return new TakeOnReportResponse(
        report.getId(),
        report.getBatchId(),
        report.getTrustAccountId(),
        report.getCutoverDate(),
        report.getSnapshotSha256(),
        report.getGeneratedAt(),
        report.getSnapshot());
  }

  // --- the snapshot (the trust commit's transaction) ---

  /**
   * Builds and saves the report of {@code batch}, committed under {@code signOff} as {@code plan},
   * the books having read {@code figures} after posting (R-9). Mandatory: only inside the commit.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public TakeOnReport snapshot(
      TakeOnBatch batch,
      TakeOnSignOff signOff,
      TrustOpeningPlan plan,
      TrustOpeningSelfCheck.Figures figures) {
    LocalDate c = batch.getCutoverDate();
    TrustAccount g = account(plan.bankingAccountId());
    boolean midMonth = batch.isMidMonthAcknowledged() && !TrustMonthlyListService.isMonthEnd(c);

    var previous =
        position(
            batch.getBankBalance(),
            batch.getOutstandingDepositsTotal(),
            batch.getOutstandingPaymentsTotal(),
            batch.getCashbookBalance(),
            batch.getFundLedgerBalance(),
            batch.getInvestedTotal());
    var banking = figures.banking();
    // Kazi holds no bank statement at the cutover date: the bank line is the one entered.
    var kazi =
        position(
            batch.getBankBalance(),
            banking.outstandingDeposits(),
            banking.outstandingPayments(),
            banking.position().cashbook(),
            banking.position().fundLedger(),
            figures.invested());
    var differences =
        position(
            BigDecimal.ZERO,
            minus(banking.outstandingDeposits(), batch.getOutstandingDepositsTotal()),
            minus(banking.outstandingPayments(), batch.getOutstandingPaymentsTotal()),
            minus(banking.position().cashbook(), batch.getCashbookBalance()),
            minus(banking.position().fundLedger(), batch.getFundLedgerBalance()),
            minus(figures.invested(), batch.getInvestedTotal()));

    var summary = TrustPositionSummary.from(batch.getSummary());
    var flags = new ArrayList<String>();
    if (midMonth) {
      flags.add(TakeOnReportSnapshot.MID_MONTH_CUTOVER);
    }
    summary.ifPresent(s -> flags.addAll(s.flags()));

    var snapshot =
        new TakeOnReportSnapshot(
            new FirmHeader(firmName()),
            new AccountHeader(
                g.getId(),
                g.getAccountName(),
                g.getAccountNumber(),
                g.getBankName(),
                g.getAccountType().name(),
                g.getRequireDualApproval()),
            c.toString(),
            midMonth,
            previous,
            kazi,
            differences,
            TakeOnReportSnapshot.section(g.getAccountName(), monthlyLists.liveList(g.getId(), c)),
            plan.investmentAccountIds().stream()
                .map(
                    i ->
                        TakeOnReportSnapshot.section(
                            account(i).getAccountName(), monthlyLists.liveList(i, c)))
                .toList(),
            plan.investments().stream().map(this::investment).toList(),
            plan.unidentifiedItems().stream()
                .map(
                    u ->
                        new UnidentifiedLine(
                            u.receivedDate().toString(),
                            ChronoUnit.DAYS.between(u.receivedDate(), c),
                            TakeOnReportSnapshot.amount(u.amount()),
                            u.bankReference() != null ? u.bankReference() : u.description()))
                .toList(),
            plan.outstandingItems().stream()
                .map(
                    o ->
                        new OutstandingLine(
                            o.kind().name(),
                            o.originalDate().toString(),
                            o.reference(),
                            o.clientId(),
                            o.clientName(),
                            o.matterId(),
                            o.matterName(),
                            TakeOnReportSnapshot.amount(o.amount())))
                .toList(),
            summary.map(TrustPositionSummary::equation).orElse(null),
            List.copyOf(flags),
            signOffSection(batch, signOff, g),
            sources.findByBatchIdOrderBySectionAsc(batch.getId()).stream()
                .map(this::source)
                .toList(),
            batch.getId());

    var report =
        reports.saveAndFlush(
            new TakeOnReport(
                batch.getId(),
                g.getId(),
                c,
                snapshot.toMap(),
                batch.getCommitRequestedBy(),
                clock.instant()));
    audit.reportGenerated(report);
    return report;
  }

  private InvestmentLine investment(TrustOpeningPlan.Investment i) {
    return new InvestmentLine(
        i.basis().name(),
        i.clientId(),
        i.clientName(),
        i.matterId(),
        i.matterName(),
        i.investmentAccountId(),
        account(i.investmentAccountId()).getAccountName(),
        i.institution(),
        i.accountNumber(),
        TakeOnReportSnapshot.amount(i.principal()),
        i.interestRatePercent().stripTrailingZeros().toPlainString(),
        i.depositDate().toString(),
        TakeOnReportSnapshot.amount(i.accruedInterest()),
        TakeOnReportSnapshot.amount(i.fundAmount()),
        TakeOnReportSnapshot.amount(i.balancePerConfirmation()),
        i.instructionReference(),
        i.instructionDate() == null ? null : i.instructionDate().toString(),
        i.endorsementConfirmed());
  }

  /**
   * The sign-off section: the signer, the declaration, the preparer; "Prepared and signed off by
   * the same person" when they are one member (FD-31) and "Authorised by the take-on sign-off" on a
   * dual-approval account (FD-25).
   */
  private SignOffSection signOffSection(TakeOnBatch batch, TakeOnSignOff signOff, TrustAccount g) {
    boolean samePerson = signOff.getMemberId().equals(batch.getCreatedBy());
    var notes = new ArrayList<String>();
    if (samePerson) {
      notes.add(TakeOnReportSnapshot.SAME_PERSON);
    }
    if (g.getRequireDualApproval()) {
      notes.add(TakeOnReportSnapshot.AUTHORISED_BY_SIGN_OFF);
    }
    return new SignOffSection(
        signOff.getMemberName(),
        signOff.getRoleName(),
        signOff.getStatementVersion(),
        signOff.getStatementText(),
        signOff.getSignedAt().toString(),
        memberNames.resolveName(batch.getCreatedBy()),
        samePerson,
        List.copyOf(notes));
  }

  private SourceFile source(TakeOnSource s) {
    return new SourceFile(
        s.getFileName(),
        s.getSection().name(),
        s.getSheetName(),
        s.getFileSha256(),
        s.getRowCount(),
        s.getUploadedBy() == null ? null : memberNames.resolveName(s.getUploadedBy()),
        s.getUploadedAt() == null ? null : s.getUploadedAt().toString());
  }

  private static ReconciledPosition position(
      BigDecimal bank,
      BigDecimal deposits,
      BigDecimal payments,
      BigDecimal cashbook,
      BigDecimal fund,
      BigDecimal invested) {
    BigDecimal adjusted = zero(bank).add(zero(deposits)).subtract(zero(payments));
    return new ReconciledPosition(
        TakeOnReportSnapshot.amount(zero(bank)),
        TakeOnReportSnapshot.amount(zero(deposits)),
        TakeOnReportSnapshot.amount(zero(payments)),
        TakeOnReportSnapshot.amount(adjusted),
        TakeOnReportSnapshot.amount(zero(cashbook)),
        TakeOnReportSnapshot.amount(zero(fund)),
        TakeOnReportSnapshot.amount(zero(invested)));
  }

  private static BigDecimal minus(BigDecimal a, BigDecimal b) {
    return zero(a).subtract(zero(b));
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }

  private TrustAccount account(UUID id) {
    return trustAccounts
        .findById(id)
        .orElseThrow(() -> new IllegalStateException("Trust account " + id + " is gone"));
  }

  private String firmName() {
    String orgId = RequestScopes.getOrgIdOrNull();
    return orgId == null
        ? null
        : organizations.findByExternalOrgId(orgId).map(Organization::getName).orElse(null);
  }
}

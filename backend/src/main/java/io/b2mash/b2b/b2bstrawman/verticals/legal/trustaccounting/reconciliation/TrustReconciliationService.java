package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountSection;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityFlag;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.TrustIntegrityReport;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyList;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.AbsaCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.BankStatementParseException;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.BankStatementParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.CsvBankStatementParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.FnbCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.GenericCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.NedbankCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.ParsedStatement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.StandardBankCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Service for bank statement import, format detection, and reconciliation operations. */
@Service
public class TrustReconciliationService {

  private static final String MODULE_ID = "trust_accounting";
  private static final BigDecimal AUTO_MATCH_THRESHOLD = new BigDecimal("0.80");

  /** How a period end is written in a refusal ("31 March 2026"). */
  private static final DateTimeFormatter PERIOD_END_FORMAT =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  /** The unique index behind one reconciliation per account and period end (V29, §16.7.11). */
  private static final String ACCOUNT_PERIOD_UNIQUE = "uq_trust_recon_account_period";

  private final BankStatementRepository bankStatementRepository;
  private final BankStatementLineRepository bankStatementLineRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final TrustTransactionRepository trustTransactionRepository;
  private final TrustReconciliationRepository trustReconciliationRepository;
  private final TrustIntegrityService integrityService;
  private final TrustMonthlyListService monthlyListService;
  private final TrustPostingService postingService;
  private final StorageService storageService;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final Clock clock;
  private final StatementLinePostingService statementLinePostingService;
  private final EntityManager entityManager;
  private final List<BankStatementParser> parsers;

  public TrustReconciliationService(
      BankStatementRepository bankStatementRepository,
      BankStatementLineRepository bankStatementLineRepository,
      TrustAccountRepository trustAccountRepository,
      TrustTransactionRepository trustTransactionRepository,
      TrustReconciliationRepository trustReconciliationRepository,
      TrustIntegrityService integrityService,
      TrustMonthlyListService monthlyListService,
      TrustPostingService postingService,
      StorageService storageService,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      Clock clock,
      StatementLinePostingService statementLinePostingService,
      EntityManager entityManager) {
    this.bankStatementRepository = bankStatementRepository;
    this.bankStatementLineRepository = bankStatementLineRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.trustTransactionRepository = trustTransactionRepository;
    this.trustReconciliationRepository = trustReconciliationRepository;
    this.integrityService = integrityService;
    this.monthlyListService = monthlyListService;
    this.postingService = postingService;
    this.storageService = storageService;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.clock = clock;
    this.statementLinePostingService = statementLinePostingService;
    this.entityManager = entityManager;
    // Parsers are plain classes (not Spring beans). GenericCsvParser must be last (always matches).
    this.parsers =
        List.of(
            new FnbCsvParser(),
            new StandardBankCsvParser(),
            new NedbankCsvParser(),
            new AbsaCsvParser(),
            new GenericCsvParser());
  }

  // --- DTO Records ---

  public record BankStatementResponse(
      UUID id,
      UUID trustAccountId,
      LocalDate periodStart,
      LocalDate periodEnd,
      BigDecimal openingBalance,
      BigDecimal closingBalance,
      String fileName,
      String format,
      int lineCount,
      int matchedCount,
      String status,
      UUID importedBy,
      Instant createdAt,
      Instant updatedAt,
      // FU-6b: set on a RETIRED statement only (replacedByStatementId on a replace only)
      Instant retiredAt,
      UUID retiredBy,
      String retiredReason,
      UUID replacedByStatementId,
      List<BankStatementLineResponse> lines) {}

  public record BankStatementLineResponse(
      UUID id,
      int lineNumber,
      LocalDate transactionDate,
      String description,
      String reference,
      BigDecimal amount,
      BigDecimal runningBalance,
      String matchStatus,
      UUID trustTransactionId,
      BigDecimal matchConfidence,
      // 647A (§16.4.3): the confirmed classification of a RECORDED line (null otherwise) and its
      // label; the parser's suggestion (647C: stored at import, null once auto-matched); the rows
      // a recorded line created ([] for every other line — a matched line's row existed before
      // the match).
      String classification,
      String classificationLabel,
      String suggestedClassification,
      List<UUID> createdTransactionIds) {

    /**
     * The one line mapping — used by the statement GET and by {@code StatementLinePostingService}
     * (a static factory, so that service never depends on this one).
     */
    public static BankStatementLineResponse of(
        BankStatementLine line, List<UUID> createdTransactionIds) {
      return new BankStatementLineResponse(
          line.getId(),
          line.getLineNumber(),
          line.getTransactionDate(),
          line.getDescription(),
          line.getReference(),
          line.getAmount(),
          line.getRunningBalance(),
          line.getMatchStatus(),
          line.getTrustTransactionId(),
          line.getMatchConfidence(),
          line.getClassification(),
          StatementLineClassification.find(line.getClassification())
              .map(StatementLineClassification::label)
              .orElse(null),
          line.getSuggestedClassification(),
          List.copyOf(createdTransactionIds));
    }
  }

  // --- Service Methods ---

  /** An import that replaces nothing (see the three-argument overload). */
  @Transactional
  public BankStatementResponse importBankStatement(UUID accountId, MultipartFile file) {
    return importBankStatement(accountId, file, null);
  }

  /**
   * Imports a bank statement (FU-6b, ruling F-646C-1): refused (409, {@code
   * bank_statement.duplicate_end_date}, naming the existing statement in {@code
   * existingStatementId}) when another non-RETIRED statement of the account ends on the same date —
   * unless {@code replacesStatementId} names the statement this one replaces. A replace retires
   * that statement in the same transaction ({@link #retire}): its matches are released, it is
   * marked RETIRED (replaced by this one), and a draft reconciliation on it is re-pointed to this
   * one and recalculated. A replace is refused while the old statement is reconciled, is used by a
   * completed reconciliation, or has a recorded line; and when a draft on it cannot be re-pointed
   * (this statement does not fit its period end). Every refusal stores nothing.
   *
   * <p>Lock order (a replace): the old statement's reconciliation rows, its lines, the trust
   * transactions they release, the old statement — then, for every import, the account's import
   * lock ({@link BankStatementRepository#lockImportsOfAccount}, a transaction-scoped advisory lock
   * keyed by tenant schema and account), which serialises two imports of one account so two
   * same-end-date imports cannot both pass the check. No trust account row is locked, so postings
   * on the account never wait on an import. The advisory lock cannot close a cycle: only an import
   * takes it, and after it an import waits on nothing another import holds — it inserts new rows,
   * writes the old statement and drafts it already locked, and reads the books plainly. A second
   * import of the account waits on it holding only its own old statement's rows, which the first
   * never asks for after taking it (a contended old statement is waited on before it, in steps 1–3
   * of {@link #prepareRetirement}). The raw file is uploaded last, once every refusal has been
   * passed.
   */
  @Transactional
  public BankStatementResponse importBankStatement(
      UUID accountId, MultipartFile file, UUID replacesStatementId) {
    moduleGuard.requireModule(MODULE_ID);

    var account =
        trustAccountRepository
            .findById(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));
    // 647C found.md #1 (648B): on a section 86(4) investment account interest is the client's —
    // the parser (which does not know the account) suggests FUND_INTEREST; it is stored as
    // INVESTMENT_INTEREST, the classification that account accepts.
    boolean section864 = account.getSection() == TrustAccountSection.S86_4;

    String tenantSchema = RequestScopes.requireTenantId();
    UUID memberId = RequestScopes.requireMemberId();
    String fileName = sanitizeFileName(file.getOriginalFilename());

    // Read file bytes so we can use them for both parsing and S3 upload
    byte[] fileBytes;
    try {
      fileBytes = file.getBytes();
    } catch (IOException e) {
      throw new BankStatementParseException("Failed to read uploaded file", e);
    }

    // Detect format: read first line for header detection
    String headerLine = readFirstLine(fileBytes);

    // Find matching parser
    BankStatementParser matchedParser = null;
    for (BankStatementParser parser : parsers) {
      if (parser.canParse(fileName, headerLine)) {
        matchedParser = parser;
        break;
      }
    }

    if (matchedParser == null) {
      throw new BankStatementParseException("No suitable parser found for file: " + fileName);
    }

    // Parse the statement
    ParsedStatement parsed;
    try {
      parsed = matchedParser.parse(new ByteArrayInputStream(fileBytes));
    } catch (IOException e) {
      throw new BankStatementParseException("Failed to parse bank statement file", e);
    }
    requireNoLineAfterToday(parsed);

    // FU-6b: the statement this import replaces — its reconciliations, lines, released rows and
    // the statement itself are locked and checked first (refusals roll everything back).
    Retirement replaced =
        replacesStatementId == null
            ? null
            : prepareRetirement(replacesStatementId, accountId, true);
    // The account's import lock last (a transaction-scoped advisory lock, not the account row):
    // two imports of one account are serialised, so two same-end-date statements cannot both pass
    // the check below, while postings on the account never wait on an import.
    bankStatementRepository.lockImportsOfAccount(accountId);
    requireNoLiveStatementEndingOn(accountId, endDateOf(parsed), replacesStatementId);

    // G88-F33: a fresh uuid segment per import, so a re-import of a same-named file (banks export
    // fixed names such as statement.csv) never overwrites the earlier raw file. Uploaded last
    // (below), so a refused import leaves no orphaned object.
    String s3Key =
        "trust-statements/%s/%s/%s/%s"
            .formatted(tenantSchema, accountId, UUID.randomUUID(), fileName);

    // Determine format label from parser class name
    String format = detectFormat(matchedParser);

    // Create BankStatement entity
    var statement =
        new BankStatement(
            accountId,
            parsed.periodStart(),
            parsed.periodEnd(),
            parsed.openingBalance(),
            parsed.closingBalance(),
            s3Key,
            fileName,
            format,
            parsed.lines().size(),
            memberId);

    statement = bankStatementRepository.save(statement);

    // Create BankStatementLine entities
    var savedLines = new java.util.ArrayList<BankStatementLine>();
    for (int i = 0; i < parsed.lines().size(); i++) {
      var parsedLine = parsed.lines().get(i);
      var line =
          new BankStatementLine(
              statement.getId(),
              i + 1,
              parsedLine.date(),
              parsedLine.description(),
              parsedLine.reference(),
              parsedLine.amount(),
              parsedLine.runningBalance());
      // 647C (§16.3.5): the parser's suggestion, stored at import; never acted on by the system.
      var suggested = parsedLine.suggestedClassification();
      if (section864 && suggested == StatementLineClassification.FUND_INTEREST) {
        suggested = StatementLineClassification.INVESTMENT_INTEREST;
      }
      if (suggested != null) {
        line.setSuggestedClassification(suggested.name());
      }
      savedLines.add(bankStatementLineRepository.save(line));
    }

    // Audit the import
    var importDetails = new LinkedHashMap<String, Object>();
    importDetails.put("trust_account_id", accountId.toString());
    importDetails.put("file_name", fileName);
    importDetails.put("format", format);
    importDetails.put("line_count", parsed.lines().size());
    if (replacesStatementId != null) {
      importDetails.put("replaces_statement_id", replacesStatementId.toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("bank_statement.imported")
            .entityType("bank_statement")
            .entityId(statement.getId())
            .details(importDetails)
            .build());

    if (replaced != null) {
      retire(replaced, memberId, "Replaced by " + fileName, statement);
    }

    storageService.upload(s3Key, fileBytes, file.getContentType());
    return toResponse(statement, savedLines);
  }

  /**
   * The end date the period-fit rule uses for a statement ({@code requireStatementFitsPeriodEnd}):
   * its last line's date, or its period end when it has no lines.
   */
  private static LocalDate endDateOf(ParsedStatement parsed) {
    return parsed.lines().stream()
        .map(line -> line.date())
        .max(Comparator.naturalOrder())
        .orElse(parsed.periodEnd());
  }

  /**
   * FU-6b (ruling F-646C-1): refuses (409) when a non-RETIRED statement of the account other than
   * {@code replacesStatementId} ends on {@code endDate}. Two such statements would both fit any
   * period end from that date (rule 3 looks only for lines after the selected statement's last
   * line), so a month could be reconciled against the superseded one. A plain import's
   * ProblemDetail ({@code bank_statement.duplicate_end_date}) names the existing statement ({@code
   * existingStatementId}, {@code existingFileName}) so the UI can offer to replace it. An import
   * that already replaces a statement is refused with {@code
   * bank_statement.duplicate_end_date_while_replacing} and no replace invitation (FU-6b D-2: one
   * import retires at most one statement).
   */
  private void requireNoLiveStatementEndingOn(
      UUID accountId, LocalDate endDate, UUID replacesStatementId) {
    var existing =
        bankStatementRepository.findLiveStatementsEndingOn(accountId, endDate).stream()
            .filter(ref -> !ref.getStatementId().equals(replacesStatementId))
            .findFirst();
    if (existing.isEmpty()) {
      return;
    }
    var ref = existing.get();
    var endDateText = endDate.format(PERIOD_END_FORMAT);
    if (replacesStatementId != null) {
      // FU-6b D-2 (ruling): one import retires at most one statement. This import already
      // replaces a statement, so it never invites a second replace — the clash is discarded first.
      // A reconciled statement cannot be discarded, so it is not offered as the way out.
      String wayOut =
          "RECONCILED".equals(ref.getStatus())
              ? " and is reconciled, so it cannot be discarded. Upload a statement ending on a"
                  + " different date."
              : ". Discard it from the Reconciliation page first, then upload again.";
      throw new ResourceConflictException(
          "A statement ending on this date is already imported",
          "Statement " + ref.getFileName() + " also ends on " + endDateText + wayOut,
          Map.of("code", "bank_statement.duplicate_end_date_while_replacing"));
    }
    var properties = new LinkedHashMap<String, Object>();
    properties.put("code", "bank_statement.duplicate_end_date");
    properties.put("existingStatementId", ref.getStatementId().toString());
    properties.put("existingFileName", ref.getFileName());
    throw new ResourceConflictException(
        "A statement ending on this date is already imported",
        "Statement "
            + ref.getFileName()
            + " of this trust account also ends on "
            + endDateText
            + ". Replace it with this statement, or import a statement that ends on another"
            + " date.",
        properties);
  }

  // --- Replace / discard (FU-6b) ---

  /**
   * A statement checked and locked for retirement, with its matches already released: the statement
   * row, and the reconciliations on it (each locked; only DRAFTs reach here).
   */
  private record Retirement(BankStatement statement, List<TrustReconciliation> drafts) {}

  /**
   * Discards an imported statement (FU-6b, F-652C-1: the statement left behind when the wizard
   * refused it, or one uploaded in error): it is retired without a replacement, with {@code
   * reason}. Its file and lines are kept; it stops counting in the period-fit rules and {@code
   * UNRESOLVED_STATEMENT_LINES}. Refused while it is reconciled, used by a completed reconciliation
   * or has a recorded line (the rules of a replace) and — FU-6b default — while a draft
   * reconciliation uses it: discarding would leave that draft without a statement; continue the
   * draft with another statement (or replace this one) first. Lock order as a replace.
   */
  @Transactional
  public BankStatementResponse discardBankStatement(UUID statementId, String reason) {
    moduleGuard.requireModule(MODULE_ID);
    if (reason == null || reason.isBlank()) {
      throw new InvalidStateException(
          "Cannot discard statement", "Give a reason for discarding this statement.");
    }
    var retirement = prepareRetirement(statementId, null, false);
    retire(retirement, RequestScopes.requireMemberId(), reason.strip(), null);
    var statement = retirement.statement();
    return toResponse(
        statement, bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(statementId));
  }

  /**
   * Locks and checks {@code statementId} for a replace ({@code replacing}, by an import into {@code
   * accountId}) or a discard, and releases its matches. In the order the default FU-6 D-1 and the
   * global lock order set (reconciliation → line → transaction → statement, as {@code
   * completeReconciliation} and {@link #unmatch}):
   *
   * <ol>
   *   <li>every reconciliation on the statement, each row locked in id order (its first read): a
   *       COMPLETED one refuses; a DRAFT refuses a discard and is re-pointed by a replace;
   *   <li>every line of the statement, locked in auto-match's order ({@link
   *       BankStatementLineRepository#findByStatementIdForUpdate}): a RECORDED line refuses (its
   *       rows are real trust entries — unmatch it first, which reverses them); each AUTO_ /
   *       MANUALLY_ MATCHED line is released through {@link #releaseMatch}, the code {@link
   *       #unmatch} uses (the line UNMATCHED, its row unlinked); EXCLUDED lines are left as they
   *       are. The released rows are locked one at a time in trust transaction id order — the order
   *       {@link #autoMatchStatement} locks its rows in — each re-read under its lock and unlinked
   *       only while it is still linked to its line;
   *   <li>the statement, locked last: refused when RETIRED, RECONCILED or of another account;
   *       {@code matched_count} falls by the lines released. Then the reconciliations on it are
   *       read again: one that a concurrent create pointed at it after step 1 refuses (create locks
   *       the statement after its reconciliation, so it either committed before this lock and is
   *       seen here, or waits and then finds the statement RETIRED).
   * </ol>
   *
   * A refusal after a release rolls the release back with the rest of the transaction.
   */
  private Retirement prepareRetirement(UUID statementId, UUID accountId, boolean replacing) {
    String title = replacing ? "Cannot replace statement" : "Cannot discard statement";

    // 1. The reconciliations on it, each locked (first read) in id order
    var lockedIds = trustReconciliationRepository.findIdsByBankStatementId(statementId);
    var drafts = new ArrayList<TrustReconciliation>();
    for (UUID reconciliationId : lockedIds) {
      var reconciliation =
          trustReconciliationRepository
              .findByIdForUpdate(reconciliationId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("TrustReconciliation", reconciliationId));
      if (!statementId.equals(reconciliation.getBankStatementId())) {
        continue; // re-pointed elsewhere while we waited for its lock
      }
      var periodEndText = reconciliation.getPeriodEnd().format(PERIOD_END_FORMAT);
      if (reconciliation.getStatus() != ReconciliationStatus.DRAFT) {
        throw new InvalidStateException(
            title,
            "The reconciliation to "
                + periodEndText
                + " was completed with this statement, so it cannot be "
                + (replacing ? "replaced." : "discarded."));
      }
      if (!replacing) {
        throw new InvalidStateException(
            title,
            "The draft reconciliation to "
                + periodEndText
                + " uses this statement. Continue that reconciliation with another statement, or"
                + " import the corrected statement as its replacement.");
      }
      drafts.add(reconciliation);
    }

    // 2. Every line, locked; recorded lines refuse; matched lines are released
    var lines = bankStatementLineRepository.findByStatementIdForUpdate(statementId);
    long recorded = lines.stream().filter(l -> "RECORDED".equals(l.getMatchStatus())).count();
    if (recorded > 0) {
      throw new InvalidStateException(
          title,
          (recorded == 1
                  ? "1 line of this statement was recorded"
                  : recorded + " lines of this statement were recorded")
              + " from the statement and created trust entries. Unmatch "
              + (recorded == 1 ? "it" : "them")
              + " first, which reverses those entries.");
    }
    // The matched lines' rows are locked in trust transaction id order (UUID.compareTo), the order
    // auto-match locks its rows in (planned.sort): a run holding a lower id never waits on a higher
    // one this retire holds while the retire waits on the lower (CodeRabbit round 1 on PR #1831).
    var matched =
        lines.stream()
            .filter(
                l ->
                    "AUTO_MATCHED".equals(l.getMatchStatus())
                        || "MANUALLY_MATCHED".equals(l.getMatchStatus()))
            .sorted(
                Comparator.comparing(
                    BankStatementLine::getTrustTransactionId,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    int released = 0;
    for (var line : matched) {
      releaseMatch(line);
      released++;
    }

    // 3. The statement last, locked (its first read), then its reconciliations again
    var statement =
        bankStatementRepository
            .findByIdForUpdate(statementId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatement", statementId));
    if (accountId != null && !statement.getTrustAccountId().equals(accountId)) {
      throw new InvalidStateException(
          "Trust account mismatch",
          "The statement to replace does not belong to the specified trust account");
    }
    if (statement.isRetired()) {
      throw new InvalidStateException(
          title,
          "This statement was already "
              + (statement.getReplacedByStatementId() != null ? "replaced." : "discarded."));
    }
    if ("RECONCILED".equals(statement.getStatus())) {
      throw new InvalidStateException(
          title,
          "This statement's reconciliation is complete, so it cannot be "
              + (replacing ? "replaced." : "discarded."));
    }
    var nowOnIt = trustReconciliationRepository.findIdsByBankStatementId(statementId);
    var expected = drafts.stream().map(TrustReconciliation::getId).toList();
    if (!expected.containsAll(nowOnIt)) {
      throw new ResourceConflictException(
          "Reconciliation started at the same time",
          "Another member started a reconciliation with this statement at the same time. Reload"
              + " and try again.");
    }
    for (int i = 0; i < released; i++) {
      statement.decrementMatched();
    }
    return new Retirement(statement, drafts);
  }

  /**
   * Retires a prepared statement (replaced by {@code replacement}, or discarded when it is null):
   * status RETIRED with who, when and why; then, on a replace, every draft on it is re-pointed to
   * the replacement and recalculated (652C's continuation, D-5) — a draft the replacement does not
   * fit refuses the whole replace. Audited as {@code bank_statement.replaced} / {@code
   * bank_statement.discarded} (lowercase entity type {@code bank_statement}).
   */
  private void retire(
      Retirement retirement, UUID memberId, String reason, BankStatement replacement) {
    var statement = retirement.statement();
    UUID replacementId = replacement != null ? replacement.getId() : null;
    statement.retire(memberId, Instant.now(), reason, replacementId);
    bankStatementRepository.save(statement);

    var repointed = new ArrayList<String>();
    for (var draft : retirement.drafts()) {
      try {
        continueDraft(draft, replacementId);
      } catch (InvalidStateException e) {
        throw new InvalidStateException(
            "Cannot replace statement",
            "The draft reconciliation to "
                + draft.getPeriodEnd().format(PERIOD_END_FORMAT)
                + " uses the statement you are replacing, and the new statement does not fit"
                + " that period end: "
                + e.getBody().getDetail());
      }
      repointed.add(draft.getId().toString());
    }

    var details = new LinkedHashMap<String, Object>();
    details.put("trust_account_id", statement.getTrustAccountId().toString());
    details.put("file_name", statement.getFileName());
    details.put("reason", reason);
    if (replacement != null) {
      details.put("replaced_by_statement_id", replacementId.toString());
      details.put("replaced_by_file_name", replacement.getFileName());
      details.put("repointed_reconciliation_ids", repointed);
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType(replacement != null ? "bank_statement.replaced" : "bank_statement.discarded")
            .entityType("bank_statement")
            .entityId(statement.getId())
            .details(details)
            .build());
  }

  /**
   * FU-6a (ruling F-647C): a statement with any line dated after today ({@code
   * LocalDate.now(clock)}, the SA business date — as the fee-transfer and reversal dates) is
   * refused whole, before anything is stored; no line is dropped. The refusal names the first such
   * line in file order.
   */
  private void requireNoLineAfterToday(ParsedStatement parsed) {
    var today = LocalDate.now(clock);
    for (var line : parsed.lines()) {
      if (line.date().isAfter(today)) {
        throw new InvalidStateException(
            "Cannot import bank statement",
            "This statement has a line dated "
                + VariableFormatter.formatDateText(line.date())
                + ", which is after today. Import the statement once the bank has processed that"
                + " line.");
      }
    }
  }

  /**
   * The account's statements, latest period end first. RETIRED statements (replaced or discarded,
   * FU-6b) are left out unless {@code includeRetired}; a retired one carries its status and who,
   * when and why.
   */
  @Transactional(readOnly = true)
  public List<BankStatementResponse> listBankStatements(UUID accountId, boolean includeRetired) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    var statements =
        includeRetired
            ? bankStatementRepository.findByTrustAccountIdOrderByPeriodEndDesc(accountId)
            : bankStatementRepository.findByTrustAccountIdAndStatusNotOrderByPeriodEndDesc(
                accountId, BankStatement.RETIRED);
    return statements.stream().map(stmt -> toResponse(stmt, List.of())).toList();
  }

  @Transactional(readOnly = true)
  public BankStatementResponse getBankStatement(UUID statementId) {
    moduleGuard.requireModule(MODULE_ID);

    var statement =
        bankStatementRepository
            .findById(statementId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatement", statementId));

    var lines =
        bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(statement.getId());

    return toResponse(statement, lines);
  }

  // --- Matching DTOs ---

  public record MatchResult(int totalLines, int autoMatched, int alreadyMatched, int unmatched) {}

  // --- Auto-Match ---

  /**
   * Auto-matches the statement's {@code UNMATCHED} lines to posted, unlinked rows of its account
   * dated within the period ±7 days, by the tiers below (exact reference; amount and date; the 0.60
   * / 0.40 suggestions; 652H D-1/D-6 and FU-16 for REVERSED originals and their contras).
   *
   * <p>Lock order (CF-3) is the one every line writer uses, line → transaction → statement ({@code
   * TrustPostingService}'s global order, step 1), and every locked row is read fresh under its
   * lock:
   *
   * <ol>
   *   <li>the statement's {@code UNMATCHED} lines, all in one locking query, before anything else
   *       is locked ({@link BankStatementLineRepository#findUnmatchedByStatementIdForUpdate}). A
   *       line that a concurrent record, manual match or exclude took is not returned, so it is
   *       never overwritten;
   *   <li>the rows this run matches, one at a time in id order, each re-read under its lock. A row
   *       that a concurrent manual match (or an auto-match of another statement) linked in the
   *       meantime, or whose status changed, is skipped and its line stays {@code UNMATCHED};
   *   <li>the statement, last, re-read under its lock: {@code matched_count} rises by the matches
   *       made, from its current value.
   * </ol>
   *
   * No client anchor or account row is locked. Manual match, unmatch, exclude and record-from-line
   * each lock one line first, then their rows (and item or investment), with the statement after
   * the line. Complete locks reconciliation → statement → account. Another auto-match locks its own
   * lines, then its rows in the same id order. So no path waits on auto-match while holding
   * something auto-match is waiting for. Every match is decided before any row is locked, against
   * the pool as read, so the tiers and their outcomes do not change.
   */
  @Transactional
  public MatchResult autoMatchStatement(UUID statementId) {
    moduleGuard.requireModule(MODULE_ID);

    // A plain read for its account and period (fixed at import); re-read under its lock in step 3.
    var statement =
        bankStatementRepository
            .findById(statementId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatement", statementId));
    // FU-6b: a RETIRED statement is not auto-matched (re-checked under its lock in step 3)
    StatementLines.requireNotRetired(statement);

    // 1. Every UNMATCHED line, locked first (matching order: date, then id), state re-checked.
    List<BankStatementLine> unmatchedLines =
        bankStatementLineRepository.findUnmatchedByStatementIdForUpdate(statementId).stream()
            .filter(line -> "UNMATCHED".equals(line.getMatchStatus()))
            .toList();

    // Build candidate pool: posted transactions (RECORDED, APPROVED or REVERSED), unmatched,
    // within date range; only the REVERSED originals auto-match may link stay in it (652H D-6).
    // A live row already linked to a line still shadows a REVERSED original (FU-16, 652H M-1).
    // One read of the window, split in memory, so a concurrent unmatch cannot drop a row from
    // both halves (plain read, no lock — CF-3 order unchanged).
    LocalDate startDate = statement.getPeriodStart().minusDays(7);
    LocalDate endDate = statement.getPeriodEnd().plusDays(7);
    Map<Boolean, List<TrustTransaction>> byLinked =
        trustTransactionRepository
            .findPostedInAutoMatchWindow(statement.getTrustAccountId(), startDate, endDate)
            .stream()
            .collect(Collectors.partitioningBy(t -> t.getBankStatementLineId() != null));
    List<TrustTransaction> linkedLiveRows =
        byLinked.get(true).stream().filter(t -> !isReversed(t)).toList();
    List<TrustTransaction> candidatePool =
        withAutoMatchableReversedOriginalsOnly(byLinked.get(false), linkedLiveRows);

    List<PlannedMatch> planned = new ArrayList<>();
    for (BankStatementLine line : unmatchedLines) {
      // Filter candidates by sign compatibility
      List<TrustTransaction> signFilteredCandidates =
          candidatePool.stream().filter(c -> isSignCompatible(line, c)).toList();

      if (signFilteredCandidates.isEmpty()) {
        continue;
      }

      // Try matching strategies in priority order; a matched row leaves the candidate pool
      TrustTransaction byReference = exactReferenceMatch(line, signFilteredCandidates);
      if (byReference != null) {
        plan(planned, candidatePool, line, byReference, new BigDecimal("1.00"));
        continue;
      }

      // A member of a reversed pair — the REVERSED original (652H D-6) or its REVERSAL contra
      // (FU-16, HF-7) — matches only by its own reference (tier 1), never by amount/date. At
      // tiers 2 and 3 it still counts, so it keeps an otherwise-unique row ambiguous (FU-16
      // review), but it is never itself matched or suggested.
      TrustTransaction byAmountAndDate = amountExactDateMatch(line, signFilteredCandidates);
      if (byAmountAndDate != null && !isReversalPairMember(byAmountAndDate)) {
        plan(planned, candidatePool, line, byAmountAndDate, AUTO_MATCH_THRESHOLD);
        continue;
      }

      TrustTransaction byAmountAndCloseDate = amountCloseDateMatch(line, signFilteredCandidates);
      if (byAmountAndCloseDate != null && !isReversalPairMember(byAmountAndCloseDate)) {
        // Confidence 0.60 -- below threshold, remains UNMATCHED
        suggest(line, byAmountAndCloseDate);
        continue;
      }

      // Tier 4 links no row; its 0.40 hint stays about live rows only (652H D-6, FU-16)
      tryAmountOnlyMatch(
          line, signFilteredCandidates.stream().filter(c -> !isReversalPairMember(c)).toList());
    }

    // 2. The matched rows, locked in id order (two auto-matches never lock two rows crosswise).
    planned.sort(Comparator.comparing(match -> match.transaction().getId()));
    int autoMatched = 0;
    for (PlannedMatch match : planned) {
      if (applyAutoMatch(match)) {
        autoMatched++;
      }
    }

    // 3. The statement last, re-read under its lock: the count rises from its current value.
    entityManager.refresh(statement, LockModeType.PESSIMISTIC_WRITE);
    // A retire that held the lines while this run waited released them (UNMATCHED again), so the
    // run may have matched lines of a now-RETIRED statement: refuse, rolling every match back.
    StatementLines.requireNotRetired(statement);
    int alreadyMatched = statement.getMatchedCount();
    statement.setMatchedCount(alreadyMatched + autoMatched);
    bankStatementRepository.save(statement);

    int totalLines = statement.getLineCount();
    // Compute actual unmatched count: unmatchedLines only contains UNMATCHED (not EXCLUDED) lines,
    // so subtracting autoMatched gives the remaining truly unmatched lines.
    int totalUnmatched = unmatchedLines.size() - autoMatched;

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("bank_statement.auto_matched")
            .entityType("bank_statement")
            .entityId(statementId)
            .details(
                Map.of(
                    "auto_matched", autoMatched,
                    "unmatched", totalUnmatched))
            .build());

    return new MatchResult(totalLines, autoMatched, alreadyMatched, totalUnmatched);
  }

  // --- Manual Match ---

  @Transactional
  public void manualMatch(UUID lineId, UUID transactionId) {
    moduleGuard.requireModule(MODULE_ID);

    // Locked before its state is read (647A): a concurrent record-from-statement cannot be
    // overwritten by this match acting on a stale UNMATCHED state.
    var line =
        bankStatementLineRepository
            .findByIdForUpdate(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatementLine", lineId));

    if (!"UNMATCHED".equals(line.getMatchStatus())) {
      throw new InvalidStateException(
          "Invalid match state", "Bank statement line is already " + line.getMatchStatus());
    }

    // Acquire pessimistic write lock to prevent concurrent match claims
    var transaction =
        trustTransactionRepository
            .findByIdForUpdate(transactionId)
            .orElseThrow(() -> new ResourceNotFoundException("TrustTransaction", transactionId));

    if (transaction.getBankStatementLineId() != null) {
      throw new InvalidStateException(
          "Invalid match state", "Transaction is already matched to a bank statement line");
    }

    // A posted row can be matched — RECORDED, APPROVED or REVERSED (a reversed original keeps its
    // cashbook effect and still shows on the bank statement, 652H). Matching never changes its
    // status (linkBankLine).
    if (transaction.getPostedAt() == null) {
      throw new InvalidStateException(
          "Invalid match state",
          "Transaction must be posted (recorded, approved or reversed) to match");
    }
    if (isReversedStatementOnlyRow(transaction)) {
      throw new InvalidStateException(
          "Invalid match state",
          "This entry was recorded from a statement line and has since been reversed, so it cannot"
              + " be matched to a bank line. Record the line again instead.");
    }

    // Verify the line and transaction belong to the same trust account (statement locked after
    // the line: it serialises the matched_count update)
    var statement =
        bankStatementRepository
            .findByIdForUpdate(line.getBankStatementId())
            .orElseThrow(
                () -> new ResourceNotFoundException("BankStatement", line.getBankStatementId()));
    StatementLines.requireNotRetired(statement);
    if (!statement.getTrustAccountId().equals(transaction.getTrustAccountId())) {
      throw new InvalidStateException(
          "Trust account mismatch",
          "Bank statement line and transaction belong to different trust accounts");
    }

    // Validate amount equality (transaction amount must equal absolute line amount)
    if (transaction.getAmount().compareTo(line.getAmount().abs()) != 0) {
      throw new InvalidStateException(
          "Amount mismatch", "Transaction amount does not match bank statement line amount");
    }

    // Validate sign compatibility (credit line must match credit transaction type, etc.)
    if (!isSignCompatible(line, transaction)) {
      throw new InvalidStateException(
          "Sign mismatch", "Transaction type is not compatible with bank statement line sign");
    }

    // Link both sides
    line.setMatchStatus("MANUALLY_MATCHED");
    line.setTrustTransactionId(transactionId);
    line.setMatchConfidence(BigDecimal.ONE);
    bankStatementLineRepository.save(line);

    postingService.linkBankLine(transaction, lineId);

    // Update matched count (statement already loaded above for trust account validation)
    statement.incrementMatched();
    bankStatementRepository.save(statement);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("bank_statement_line.manually_matched")
            .entityType("bank_statement_line")
            .entityId(lineId)
            .details(Map.of("trust_transaction_id", transactionId.toString()))
            .build());
  }

  // --- Unmatch ---

  @Transactional
  public void unmatch(UUID lineId) {
    moduleGuard.requireModule(MODULE_ID);

    // Locked before its state is read (647A): a second concurrent unmatch waits and then sees the
    // first one's result, instead of acting on a stale RECORDED / matched state.
    var line =
        bankStatementLineRepository
            .findByIdForUpdate(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatementLine", lineId));

    // A recorded line created its rows: unmatching it reverses them, on the line's date (647A).
    if ("RECORDED".equals(line.getMatchStatus())) {
      statementLinePostingService.unmatch(lineId);
      return;
    }

    if (!"AUTO_MATCHED".equals(line.getMatchStatus())
        && !"MANUALLY_MATCHED".equals(line.getMatchStatus())) {
      throw new InvalidStateException(
          "Invalid match state", "Bank statement line must be matched to unmatch");
    }

    releaseMatch(line);

    // Decrement matched count (statement locked after the line); refused once the statement is
    // RECONCILED (D-14) or RETIRED (FU-6b) — the refusal rolls back the line and transaction
    // changes above.
    var statement =
        bankStatementRepository
            .findByIdForUpdate(line.getBankStatementId())
            .orElseThrow(
                () -> new ResourceNotFoundException("BankStatement", line.getBankStatementId()));
    StatementLines.requireNotReconciled(statement);
    StatementLines.requireNotRetired(statement);
    statement.decrementMatched();
    bankStatementRepository.save(statement);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("bank_statement_line.unmatched")
            .entityType("bank_statement_line")
            .entityId(lineId)
            .build());
  }

  /**
   * Releases a matched ({@code AUTO_MATCHED} / {@code MANUALLY_MATCHED}) line, already locked: the
   * line goes back to {@code UNMATCHED} and its row is unlinked ({@code linkBankLine(row, null)},
   * which never touches the row's effects). The caller locks the statement next and lowers its
   * {@code matched_count}. Used by {@link #unmatch} and by a replace or discard (FU-6b).
   */
  private void releaseMatch(BankStatementLine line) {
    UUID transactionId = line.getTrustTransactionId();

    // Clear the line side
    line.setMatchStatus("UNMATCHED");
    line.setTrustTransactionId(null);
    line.setMatchConfidence(null);
    bankStatementLineRepository.save(line);

    // Clear the transaction side. The row is locked and read fresh (line → transaction →
    // statement): the save writes the whole row, so a plain read could write back a stale status
    // over a reversal committed meanwhile (a reversal locks the row, never the line — FU-6b
    // review). This is the first read of the row in every caller's transaction. Re-read under its
    // lock, the row is unlinked only while it is still linked to this line.
    if (transactionId != null) {
      trustTransactionRepository
          .findByIdForUpdate(transactionId)
          .filter(txn -> line.getId().equals(txn.getBankStatementLineId()))
          .ifPresent(txn -> postingService.linkBankLine(txn, null));
    }
  }

  // --- Exclude ---

  @Transactional
  public void excludeLine(UUID lineId, String reason) {
    moduleGuard.requireModule(MODULE_ID);

    // Locked before its state is read (647A): see manualMatch.
    var line =
        bankStatementLineRepository
            .findByIdForUpdate(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatementLine", lineId));

    if (!"UNMATCHED".equals(line.getMatchStatus())) {
      throw new InvalidStateException(
          "Invalid match state", "Only UNMATCHED lines can be excluded");
    }
    // FU-6b: never a line of a RETIRED statement. A plain read after the line lock: a retire locks
    // every line of the statement before it retires it, so this read sees its committed status.
    bankStatementRepository
        .findById(line.getBankStatementId())
        .ifPresent(StatementLines::requireNotRetired);

    // Intentionally does NOT update matchedCount: excluded lines are not "matched" in the
    // reconciliation sense. matchedCount tracks matched (AUTO_MATCHED + MANUALLY_MATCHED) and
    // recorded (RECORDED, 647A) lines, so the reconciliation progress percentage remains accurate.
    line.setMatchStatus("EXCLUDED");
    line.setExcludedReason(reason);
    bankStatementLineRepository.save(line);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("bank_statement_line.excluded")
            .entityType("bank_statement_line")
            .entityId(lineId)
            .details(Map.of("reason", reason))
            .build());
  }

  // --- Reconciliation DTOs ---

  /**
   * The creditor side of a reconciliation as at its period end (§16.1.2 {@code creditors(A,D)}):
   * the six terms and their total. {@code otherNonClient} has no column (always zero in Phase 92):
   * it is the stored total minus the five stored terms.
   */
  public record CreditorBreakdown(
      BigDecimal clientLedgers,
      BigDecimal investmentCreditors,
      BigDecimal fundInterestAndCharges,
      BigDecimal unidentifiedDeposits,
      BigDecimal otherNonClient,
      BigDecimal interAccount,
      BigDecimal total) {

    /**
     * The creditor side of a live {@code position} (646D: dashboard trust position, total-balance).
     */
    public static CreditorBreakdown of(TrustBooksService.TrustPosition position) {
      return new CreditorBreakdown(
          position.clientLedgers(),
          position.investmentCreditors(),
          position.fundLedger(),
          position.unidentifiedDeposits(),
          position.otherNonClient(),
          position.interAccount(),
          position.creditorTotal());
    }
  }

  /**
   * A reconciliation (§16.4.4). Every figure is as at {@code periodEnd}. {@code
   * bankToCashbookDifference = adjusted bank − cashbook} (EQ-1), {@code
   * cashbookToCreditorsDifference = cashbook − creditors} (EQ-2), {@code difference = adjusted bank
   * − creditors}. {@code excludedLinesTotal} (signed) and {@code unresolvedLineCount} are read live
   * from the statement's lines. {@code integrityFlags} are the flags of the last calculation (649C,
   * §16.3.4 step 5): a blocking flag stops completion. {@code monthlyListId} is the Rule 54.15 list
   * this reconciliation locked when it completed to a month end (650A, ADR-382); null otherwise.
   */
  public record TrustReconciliationResponse(
      UUID id,
      UUID trustAccountId,
      LocalDate periodEnd,
      UUID bankStatementId,
      BigDecimal bankBalance,
      BigDecimal outstandingDeposits,
      BigDecimal outstandingPayments,
      BigDecimal adjustedBankBalance,
      BigDecimal cashbookBalance,
      BigDecimal clientLedgerTotal,
      CreditorBreakdown creditorBreakdown,
      BigDecimal bankToCashbookDifference,
      BigDecimal cashbookToCreditorsDifference,
      BigDecimal difference,
      BigDecimal excludedLinesTotal,
      int unresolvedLineCount,
      boolean isBalanced,
      List<IntegrityFlag> integrityFlags,
      String status,
      UUID completedBy,
      Instant completedAt,
      UUID monthlyListId,
      String notes,
      Instant createdAt,
      Instant updatedAt) {}

  public record CreateReconciliationRequest(
      @jakarta.validation.constraints.NotNull LocalDate periodEnd,
      @jakarta.validation.constraints.NotNull UUID bankStatementId) {}

  /**
   * The body of calculate and complete: the statement the caller reviewed. A continuation can
   * re-point the draft to another statement (652C), so the service refuses (409) when the draft no
   * longer uses this one — a stale wizard never calculates or completes a statement it did not
   * review.
   */
  public record ExpectedStatementRequest(
      @jakarta.validation.constraints.NotNull UUID expectedBankStatementId) {}

  // --- Reconciliation Methods ---

  /**
   * Starts — or continues — the reconciliation of {@code accountId} as at {@code periodEnd} (the
   * firm's choice, never derived from the statement — §16.3.4, ADR-377). One reconciliation per
   * account and period end (652C, §16.3.12): an existing DRAFT is returned, re-pointed to {@code
   * bankStatementId} and recalculated; an existing COMPLETED one refuses (409). The statement is
   * required and must fit the period end, so that its closing balance is the bank balance as at
   * {@code periodEnd}: it belongs to the account, has no line after {@code periodEnd}, no other
   * imported statement of the account has a line between its last line and {@code periodEnd}, and
   * {@code periodEnd} is not in the future. The first failing rule refuses (400). A new draft is
   * not calculated here (the wizard calculates next); a returned draft is.
   */
  @Transactional
  public TrustReconciliationResponse createReconciliation(
      UUID accountId, LocalDate periodEnd, UUID bankStatementId) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    if (bankStatementId == null) {
      throw new InvalidStateException(
          "Missing bank statement",
          "Import the bank statement for this period before starting the reconciliation.");
    }
    // First read of the row, locked: a fresh status and statement, serialised with other creates
    // for the same draft and with calculate/complete; a create waiting on a completion sees it
    // COMPLETED (652C D-3). The reconciliation row is every writer's first lock (D-13).
    var existing =
        trustReconciliationRepository.findByTrustAccountIdAndPeriodEndForUpdate(
            accountId, periodEnd);
    if (existing.isPresent() && existing.get().getStatus() != ReconciliationStatus.DRAFT) {
      throw alreadyReconciled(periodEnd);
    }

    // Share-locked after the reconciliation row (reconciliation → statement, D-13), as its first
    // read: a replace or discard of this statement (FU-6b) locks it FOR UPDATE, so a create either
    // commits first (and the retire sees the reconciliation and refuses) or waits and finds it
    // RETIRED here. A share lock, so two creates on one statement do not wait on each other here.
    var statement =
        bankStatementRepository
            .findByIdForShare(bankStatementId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatement", bankStatementId));
    requireStatementFitsPeriodEnd(accountId, statement, periodEnd);

    if (existing.isPresent()) {
      return continueDraft(existing.get(), bankStatementId);
    }

    TrustReconciliation reconciliation;
    try {
      reconciliation =
          trustReconciliationRepository.saveAndFlush(
              new TrustReconciliation(accountId, periodEnd, bankStatementId));
    } catch (DataIntegrityViolationException e) {
      if (!violates(e, ACCOUNT_PERIOD_UNIQUE)) {
        throw e;
      }
      // Another create for the same account and period end committed first (652C D-3)
      throw new ResourceConflictException(
          "Reconciliation started at the same time",
          "Another member started the reconciliation of this trust account to "
              + periodEnd.format(PERIOD_END_FORMAT)
              + " at the same time. Choose Review Matches again to continue it.");
    }

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_reconciliation.created")
            .entityType("trust_reconciliation")
            .entityId(reconciliation.getId())
            .details(
                Map.of(
                    "trust_account_id",
                    accountId.toString(),
                    "period_end",
                    periodEnd.toString(),
                    "bank_statement_id",
                    bankStatementId.toString()))
            .build());

    return toReconciliationResponse(reconciliation, null);
  }

  /**
   * The DRAFT of the account and period end, re-pointed to {@code bankStatementId} (a re-upload, or
   * the wizard asking again after a reset — §16.3.12) and recalculated as at its period end.
   * Matches and exclusions belong to the statements' lines and are not touched (652C D-5). A
   * changed statement is audited; asking again with the same statement is not.
   */
  private TrustReconciliationResponse continueDraft(
      TrustReconciliation draft, UUID bankStatementId) {
    UUID previous = draft.getBankStatementId();
    draft.setBankStatementId(bankStatementId);
    recalculate(draft);
    draft = trustReconciliationRepository.save(draft);

    if (!bankStatementId.equals(previous)) {
      var details = new LinkedHashMap<String, Object>();
      details.put("trust_account_id", draft.getTrustAccountId().toString());
      details.put("period_end", draft.getPeriodEnd().toString());
      if (previous != null) {
        details.put("previous_bank_statement_id", previous.toString());
      }
      details.put("bank_statement_id", bankStatementId.toString());
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("trust_reconciliation.statement_changed")
              .entityType("trust_reconciliation")
              .entityId(draft.getId())
              .details(details)
              .build());
    }
    return toReconciliationResponse(draft, null);
  }

  /** 409: the account is already reconciled to {@code periodEnd} (§16.3.12). */
  private static ResourceConflictException alreadyReconciled(LocalDate periodEnd) {
    return new ResourceConflictException(
        "This period is already reconciled",
        "This trust account was reconciled to "
            + periodEnd.format(PERIOD_END_FORMAT)
            + ". A completed reconciliation cannot be changed; choose a later period end.");
  }

  private static boolean violates(DataIntegrityViolationException e, String constraint) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException violation
          && constraint.equalsIgnoreCase(violation.getConstraintName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The four period rules of §16.3.4, in order; the first failure refuses. Checked at create and
   * again at every calculate (a later import can make a statement stop fitting its period end).
   */
  private void requireStatementFitsPeriodEnd(
      UUID accountId, BankStatement statement, LocalDate periodEnd) {
    if (!statement.getTrustAccountId().equals(accountId)) {
      throw new InvalidStateException(
          "Trust account mismatch",
          "Bank statement does not belong to the specified trust account");
    }
    // FU-6b: a RETIRED statement (replaced or discarded) is never reconciled against
    if (statement.isRetired()) {
      throw new InvalidStateException(
          "Statement retired",
          "This bank statement was "
              + (statement.getReplacedByStatementId() != null ? "replaced" : "discarded")
              + ". Reconcile against the statement that replaced it, or import the statement for"
              + " this period.");
    }
    var periodEndText = periodEnd.format(PERIOD_END_FORMAT);
    // The parsers set period_end to the last line's date, so a statement with no lines uses its
    // period_end as its last line.
    LocalDate lastLine =
        bankStatementLineRepository
            .findLastLineDate(statement.getId())
            .orElse(statement.getPeriodEnd());
    if (lastLine.isAfter(periodEnd)) {
      throw new InvalidStateException(
          "Statement does not fit the period end",
          "This statement has lines dated after "
              + periodEndText
              + ". Choose a later period end, or import a statement that ends on or before it.");
    }
    var covering =
        bankStatementLineRepository.findOtherStatementsWithLinesBetween(
            accountId, statement.getId(), lastLine, periodEnd);
    if (!covering.isEmpty()) {
      throw new InvalidStateException(
          "Statement does not fit the period end",
          "Statement "
              + covering.getFirst()
              + " has movements between this statement's last line and "
              + periodEndText
              + ". Reconcile against the statement that covers the period end.");
    }
    if (periodEnd.isAfter(LocalDate.now(clock))) {
      throw new InvalidStateException(
          "Invalid period end", "The period end cannot be in the future");
    }
  }

  @Transactional(readOnly = true)
  public List<TrustReconciliationResponse> listReconciliations(UUID accountId) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    var reconciliations =
        trustReconciliationRepository.findByTrustAccountIdOrderByPeriodEndDesc(accountId);
    // One statement for the page's Rule 54.15 lists (only a COMPLETED reconciliation locks one)
    var monthlyListIds =
        monthlyListService.monthlyListIdsFor(
            reconciliations.stream()
                .filter(r -> r.getStatus() == ReconciliationStatus.COMPLETED)
                .map(TrustReconciliation::getId)
                .toList());
    return reconciliations.stream()
        .map(r -> toReconciliationResponse(r, monthlyListIds.get(r.getId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public TrustReconciliationResponse getReconciliation(UUID reconciliationId) {
    moduleGuard.requireModule(MODULE_ID);

    var reconciliation =
        trustReconciliationRepository
            .findById(reconciliationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustReconciliation", reconciliationId));

    return toReconciliationResponse(
        reconciliation,
        reconciliation.getStatus() == ReconciliationStatus.COMPLETED
            ? monthlyListService.monthlyListIdFor(reconciliationId)
            : null);
  }

  /**
   * Recalculates a DRAFT reconciliation as at its period end (§16.3.4 steps 1–5): the statement
   * must still fit the period end; every term, {@code is_balanced} (EQ-1 and EQ-2) and the
   * integrity flags are stored. {@code expectedBankStatementId} is the statement the caller
   * reviewed; a draft re-pointed since refuses (409) and is left unchanged.
   */
  @Transactional
  public TrustReconciliationResponse calculateReconciliation(
      UUID reconciliationId, UUID expectedBankStatementId) {
    moduleGuard.requireModule(MODULE_ID);

    var reconciliation =
        requireDraft(
            reconciliationId,
            expectedBankStatementId,
            "Cannot recalculate a COMPLETED reconciliation");
    recalculate(reconciliation);
    reconciliation = trustReconciliationRepository.save(reconciliation);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_reconciliation.calculated")
            .entityType("trust_reconciliation")
            .entityId(reconciliation.getId())
            .details(
                Map.of(
                    "trust_account_id",
                    reconciliation.getTrustAccountId().toString(),
                    "bank_balance",
                    reconciliation.getBankBalance().toString(),
                    "cashbook_balance",
                    reconciliation.getCashbookBalance().toString(),
                    "client_ledger_total",
                    reconciliation.getClientLedgerTotal().toString(),
                    "creditor_total",
                    reconciliation.getCreditorTotal().toString(),
                    "difference",
                    reconciliation.getDifference().toString(),
                    "is_balanced",
                    reconciliation.isBalanced()))
            .build());

    return toReconciliationResponse(reconciliation, null);
  }

  /**
   * Completes a DRAFT reconciliation — the gate G1–G4 (§16.3.4, ADR-377). It recalculates first and
   * never trusts stored figures (a statement imported or a posting entered since the last calculate
   * is caught here), then evaluates all four conditions and, if any fails, refuses once: the title
   * of the first failing condition in the order G1 → G4 and one sentence per failing condition in
   * the detail. Warning flags never block (a client or the Fund ledger in debit is flag-only —
   * ruling #5, OL-7, decision 9). On success the reconciliation is {@code COMPLETED}, its statement
   * {@code RECONCILED}, the flags are stored and, when the period end is the last day of a calendar
   * month, the account's Rule 54.15 list is locked in the same transaction (650A, ADR-382) — the
   * month the firm chose, never the statement's last line date. A refusal by the lock (the month
   * already locked) rolls the completion back — defence in depth only: since 652C a second
   * reconciliation to the same period end is refused at create (409 "This period is already
   * reconciled"), so the lock's refusal is not reached through this service. {@code
   * expectedBankStatementId} is the statement the caller reviewed; a draft re-pointed since refuses
   * (409) and nothing changes — no statement is marked reconciled, no list is locked.
   */
  @Transactional
  public TrustReconciliationResponse completeReconciliation(
      UUID reconciliationId, UUID expectedBankStatementId) {
    moduleGuard.requireModule(MODULE_ID);

    // Locks the reconciliation row first (requireDraft): a double-click's second complete waits
    // here and then reads COMPLETED; a concurrent re-point either commits first (and is seen
    // here) or waits for this completion and is refused as already reconciled.
    var reconciliation =
        requireDraft(
            reconciliationId, expectedBankStatementId, "Reconciliation is already COMPLETED");

    // The statement row is locked BEFORE the gate inputs are read: an unmatch of one of its lines
    // locks the same row (after the line and transaction), so the two are serialised — either the
    // unmatch commits first and G4 sees the unresolved line, or this completes first and the
    // unmatch is refused on a RECONCILED statement (D-14). Lock order: reconciliation → statement
    // → trust account (the monthly list's lock below) — 652C D-13.
    UUID statementId = reconciliation.getBankStatementId();
    BankStatement statement = null;
    if (statementId != null) {
      statement =
          bankStatementRepository
              .findByIdForUpdate(statementId)
              .orElseThrow(() -> new ResourceNotFoundException("BankStatement", statementId));
    }

    var report = recalculate(reconciliation);
    refuseUnlessComplete(report);

    reconciliation.setStatus(ReconciliationStatus.COMPLETED);
    reconciliation.setCompletedBy(RequestScopes.requireMemberId());
    reconciliation.setCompletedAt(Instant.now());
    reconciliation = trustReconciliationRepository.save(reconciliation);

    // recalculate refuses a reconciliation with no statement, so the statement is locked here
    statement.setStatus("RECONCILED");
    bankStatementRepository.save(statement);

    // A direct call, not an event: the lock must be atomic with completion (R10, ADR-382)
    TrustMonthlyList monthlyList =
        TrustMonthlyListService.isMonthEnd(reconciliation.getPeriodEnd())
            ? monthlyListService.lock(reconciliation)
            : null;

    var details = new LinkedHashMap<String, Object>();
    details.put("trust_account_id", reconciliation.getTrustAccountId().toString());
    details.put("period_end", reconciliation.getPeriodEnd().toString());
    details.put("bank_balance", reconciliation.getBankBalance().toString());
    details.put("cashbook_balance", reconciliation.getCashbookBalance().toString());
    details.put("client_ledger_total", reconciliation.getClientLedgerTotal().toString());
    details.put("creditor_total", reconciliation.getCreditorTotal().toString());
    details.put("difference", reconciliation.getDifference().toString());
    details.put("flags", report.flags().stream().map(IntegrityFlag::code).toList());
    if (monthlyList != null) {
      details.put("monthly_list_id", monthlyList.getId().toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_reconciliation.completed")
            .entityType("trust_reconciliation")
            .entityId(reconciliation.getId())
            .details(details)
            .build());

    return toReconciliationResponse(
        reconciliation, monthlyList != null ? monthlyList.getId() : null);
  }

  /**
   * The DRAFT, row-locked by the first read of it in the transaction (so it is fresh), still on the
   * statement the caller reviewed. Not found 404; not a draft 400; re-pointed to another statement
   * since the caller read it (a continuation in another tab, 652C) 409 — nothing is changed.
   */
  private TrustReconciliation requireDraft(
      UUID reconciliationId, UUID expectedBankStatementId, String detailWhenCompleted) {
    var reconciliation =
        trustReconciliationRepository
            .findByIdForUpdate(reconciliationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustReconciliation", reconciliationId));
    if (reconciliation.getStatus() != ReconciliationStatus.DRAFT) {
      throw new InvalidStateException("Invalid reconciliation state", detailWhenCompleted);
    }
    if (!Objects.equals(expectedBankStatementId, reconciliation.getBankStatementId())) {
      throw new ResourceConflictException(
          "This reconciliation now uses a different statement",
          "Since you opened it, this reconciliation was continued with another bank statement."
              + " Reload the reconciliation and review the statement it now uses before you"
              + " continue.");
    }
    return reconciliation;
  }

  /**
   * §16.3.4 steps 1–5 on {@code reconciliation} (not saved): re-checks that the statement fits the
   * period end (a statement imported since can break the period rules — the closing balance is the
   * bank balance as at the period end only while the statement still fits it), then stores every
   * term as at the period end from the integrity check (which reads them from {@link
   * TrustBooksService}), {@code is_balanced} (EQ-1 and EQ-2) and the flags. Returns the report for
   * the completion gate.
   */
  private TrustIntegrityReport recalculate(TrustReconciliation reconciliation) {
    UUID bankStatementId = reconciliation.getBankStatementId();
    if (bankStatementId == null) {
      throw new InvalidStateException(
          "Missing bank statement",
          "A bank statement must be linked to calculate the reconciliation");
    }
    UUID accountId = reconciliation.getTrustAccountId();
    LocalDate periodEnd = reconciliation.getPeriodEnd();
    var statement =
        bankStatementRepository
            .findById(bankStatementId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatement", bankStatementId));
    requireStatementFitsPeriodEnd(accountId, statement, periodEnd);

    var report = integrityService.check(accountId, periodEnd, bankStatementId);
    var position = report.position();
    var bank = report.bank();

    reconciliation.setBankBalance(bank.bankBalance());
    reconciliation.setOutstandingDeposits(bank.outstandingDeposits());
    reconciliation.setOutstandingPayments(bank.outstandingPayments());
    reconciliation.setAdjustedBankBalance(bank.adjustedBankBalance());
    reconciliation.setCashbookBalance(position.cashbook());
    reconciliation.setClientLedgerTotal(position.clientLedgers());
    reconciliation.setInvestmentCreditorTotal(position.investmentCreditors());
    reconciliation.setFundLedgerBalance(position.fundLedger());
    reconciliation.setUnidentifiedDepositTotal(position.unidentifiedDeposits());
    reconciliation.setInterAccountBalance(position.interAccount());
    reconciliation.setCreditorTotal(position.creditorTotal());
    reconciliation.setDifference(bank.adjustedBankBalance().subtract(position.creditorTotal()));
    // is_balanced is deliberately EQ-1 ∧ EQ-2 as at the period end (the 646C definition: the two
    // comparisons only); the completion gate decides on the integrity flags, not on this field.
    reconciliation.setBalanced(
        bank.adjustedBankBalance().compareTo(position.cashbook()) == 0
            && position.cashbook().compareTo(position.creditorTotal()) == 0);
    reconciliation.setIntegrityFlags(report.flags());
    return report;
  }

  /**
   * The gate G1–G4 from the report's blocking flags (the gate and the dashboard card read the same
   * check): G1 EQ-1 ({@code BANK_DIFFERENCE}), G2 EQ-2 ({@code BOOKS_OUT_OF_BALANCE}), G3 EQ-4 and
   * EQ-5 ({@code UNIDENTIFIED_ITEMS_MISMATCH}, {@code INVESTMENT_BALANCE_MISMATCH}), G4 the
   * statement's unresolved lines ({@code UNRESOLVED_STATEMENT_LINES}). Every condition is
   * evaluated; one refusal carries them all.
   */
  private static void refuseUnlessComplete(TrustIntegrityReport report) {
    String title = null;
    var sentences = new ArrayList<String>();
    for (var gate : GATES) {
      var failing =
          report.flags().stream()
              .filter(TrustIntegrityService::isBlocking)
              .filter(f -> gate.codes().contains(f.code()))
              .toList();
      if (failing.isEmpty()) {
        continue;
      }
      if (title == null) {
        title = gate.title();
      }
      failing.forEach(f -> sentences.add(f.message()));
    }
    if (title != null) {
      throw new InvalidStateException(title, String.join(" ", sentences));
    }
  }

  /** One condition of the completion gate: its refusal title and the flags that fail it. */
  private record Gate(String title, List<String> codes) {}

  /** G1 → G4 in order (§16.3.4; G3 and G4 titles: ruling D-2). */
  private static final List<Gate> GATES =
      List.of(
          new Gate(
              "Reconciliation does not balance", List.of(TrustIntegrityService.BANK_DIFFERENCE)),
          new Gate(
              "Trust books do not balance", List.of(TrustIntegrityService.BOOKS_OUT_OF_BALANCE)),
          new Gate(
              "Trust ledgers do not agree with their records",
              List.of(
                  TrustIntegrityService.UNIDENTIFIED_ITEMS_MISMATCH,
                  TrustIntegrityService.INVESTMENT_BALANCE_MISMATCH)),
          new Gate(
              "Statement lines are unresolved",
              List.of(TrustIntegrityService.UNRESOLVED_STATEMENT_LINES)));

  /** {@code monthlyListId}: the list the reconciliation locked, read by the caller (or null). */
  private TrustReconciliationResponse toReconciliationResponse(
      TrustReconciliation recon, UUID monthlyListId) {
    var namedTerms =
        recon
            .getClientLedgerTotal()
            .add(recon.getInvestmentCreditorTotal())
            .add(recon.getFundLedgerBalance())
            .add(recon.getUnidentifiedDepositTotal())
            .add(recon.getInterAccountBalance());
    var breakdown =
        new CreditorBreakdown(
            recon.getClientLedgerTotal(),
            recon.getInvestmentCreditorTotal(),
            recon.getFundLedgerBalance(),
            recon.getUnidentifiedDepositTotal(),
            recon.getCreditorTotal().subtract(namedTerms),
            recon.getInterAccountBalance(),
            recon.getCreditorTotal());
    UUID statementId = recon.getBankStatementId();
    BigDecimal excludedLinesTotal =
        statementId == null
            ? BigDecimal.ZERO
            : bankStatementLineRepository.sumExcludedAmount(statementId);
    int unresolvedLineCount =
        statementId == null
            ? 0
            : Math.toIntExact(
                bankStatementLineRepository.countByBankStatementIdAndMatchStatus(
                    statementId, "UNMATCHED"));
    return new TrustReconciliationResponse(
        recon.getId(),
        recon.getTrustAccountId(),
        recon.getPeriodEnd(),
        statementId,
        recon.getBankBalance(),
        recon.getOutstandingDeposits(),
        recon.getOutstandingPayments(),
        recon.getAdjustedBankBalance(),
        recon.getCashbookBalance(),
        recon.getClientLedgerTotal(),
        breakdown,
        recon.getAdjustedBankBalance().subtract(recon.getCashbookBalance()),
        recon.getCashbookBalance().subtract(recon.getCreditorTotal()),
        recon.getDifference(),
        excludedLinesTotal,
        unresolvedLineCount,
        recon.isBalanced(),
        List.copyOf(recon.getIntegrityFlags()),
        recon.getStatus().name(),
        recon.getCompletedBy(),
        recon.getCompletedAt(),
        monthlyListId,
        recon.getNotes(),
        recon.getCreatedAt(),
        recon.getUpdatedAt());
  }

  // --- Auto-Match Strategy Helpers ---

  /**
   * 652H D-1 (R-1): a REVERSED row of a type only ever recorded from a statement line ({@link
   * TrustTransactionType#recordedOnlyFromStatementLine}). It was born linked to its line and is
   * unlinked only once its line is unmatched (the row reversed by {@code reverseForUnmatch}, or
   * reversed generically first); it must never match a bank line again — least of all the line it
   * was recorded from. Neither auto-match nor manual match offers it.
   */
  private static boolean isReversedStatementOnlyRow(TrustTransaction transaction) {
    return isReversed(transaction)
        && TrustTransactionType.find(transaction.getTransactionType())
            .map(TrustTransactionType::recordedOnlyFromStatementLine)
            .orElse(false);
  }

  private static boolean isReversed(TrustTransaction transaction) {
    return "REVERSED".equals(transaction.getStatus());
  }

  /**
   * A member of a reversed pair: the REVERSED original, or a {@code REVERSAL} contra (posted, as
   * every pool row is). Auto-match links either at tier 1 only — its own reference and amount —
   * never at the amount/date tiers (652H D-6 for the original; FU-16 / HF-7 for the contra). There
   * it still counts toward a tier's uniqueness, so it keeps another row of its amount and date
   * ambiguous, as before FU-16; it is just never the row picked or suggested.
   *
   * <p>A contra's own line is the one carrying the contra's reference: {@code REV-} + the
   * original's, generated by {@code TrustPostingService.contraDraft} and carried by the bank's
   * return line in the exit month (§16.9.6 #8 {@code REV-DEP-B-002}, #10 {@code REV-PAY-A-002}). By
   * amount and date alone a contra cannot tell its own return from an unrelated movement of its
   * amount (HF-7: taking an unrelated, unbooked line leaves its original outstanding and the month
   * balances over the unbooked line), so such a line is left for a human, who matches the contra by
   * hand if it is the return.
   */
  private static boolean isReversalPairMember(TrustTransaction transaction) {
    return isReversed(transaction) || "REVERSAL".equals(transaction.getTransactionType());
  }

  /**
   * 652H D-1/D-6: the auto-match pool — every unmatched posted row, except the REVERSED originals
   * auto-match may not link. Computed once per run, against the initial pool (before any line
   * consumes a row), and returned mutable so the run can shrink it. A REVERSED original stays only
   * when it is not a statement-only row (D-1) and no live (non-REVERSED) row of its amount and sign
   * (cashbook direction) is in the run's window — neither in the initial pool, so a row that once
   * shadowed it keeps doing so after an earlier line takes it, nor among {@code linkedLive}, the
   * live rows already linked to a line (an earlier run, by hand, or on an earlier statement: FU-16,
   * 652H M-1). A kept REVERSED original is then offered at tier 1 only (its own reference), never
   * at the amount/date tiers ({@link #isReversalPairMember}): it matches its own line — the exit
   * month's #7/#9 (§16.9.6) — but never absorbs an unrelated line of its amount, nor takes a line
   * from a reverse-then-re-enter's corrected row. Anything else is left for a human (manual match
   * still accepts it). Contras ({@code REVERSAL} rows) are live rows for shadowing.
   */
  private static List<TrustTransaction> withAutoMatchableReversedOriginalsOnly(
      List<TrustTransaction> unmatched, List<TrustTransaction> linkedLive) {
    return unmatched.stream()
        .filter(
            c ->
                !isReversed(c)
                    || (!isReversedStatementOnlyRow(c)
                        && Stream.concat(unmatched.stream(), linkedLive.stream())
                            .noneMatch(
                                live ->
                                    !isReversed(live)
                                        && live.getAmount().compareTo(c.getAmount()) == 0
                                        && live.getCashbookEffect().signum()
                                            == c.getCashbookEffect().signum())))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /**
   * A bank line matches a posted row moving the cashbook the same way (ADR-378, §16.2.1): a credit
   * line a row with a positive {@code cashbook_effect}, a debit line a negative one.
   * Cashbook-neutral rows (transfers between clients, zero-effect reversals) never reach the bank,
   * so they never auto-match.
   */
  private boolean isSignCompatible(BankStatementLine line, TrustTransaction candidate) {
    boolean lineIsCredit = line.getAmount().compareTo(BigDecimal.ZERO) > 0;
    int effect = candidate.getCashbookEffect().signum();
    return lineIsCredit ? effect > 0 : effect < 0;
  }

  /** Tier 1: the one candidate with the line's reference and amount, else null. */
  private TrustTransaction exactReferenceMatch(
      BankStatementLine line, List<TrustTransaction> candidates) {
    if (line.getReference() == null || line.getReference().isBlank()) {
      return null;
    }

    List<TrustTransaction> refMatches =
        candidates.stream()
            .filter(
                c ->
                    c.getReference() != null
                        && c.getReference().equalsIgnoreCase(line.getReference())
                        && c.getAmount().compareTo(line.getAmount().abs()) == 0)
            .toList();

    return refMatches.size() == 1 ? refMatches.getFirst() : null;
  }

  /** Tier 2: the one candidate with the line's amount and date, else null. */
  private TrustTransaction amountExactDateMatch(
      BankStatementLine line, List<TrustTransaction> candidates) {
    BigDecimal lineAmount = line.getAmount().abs();

    List<TrustTransaction> matches =
        candidates.stream()
            .filter(
                c ->
                    c.getAmount().compareTo(lineAmount) == 0
                        && c.getTransactionDate().equals(line.getTransactionDate()))
            .toList();

    return matches.size() == 1 ? matches.getFirst() : null;
  }

  /** Tier 3: the one candidate with the line's amount, dated within 3 days of it, else null. */
  private TrustTransaction amountCloseDateMatch(
      BankStatementLine line, List<TrustTransaction> candidates) {
    BigDecimal lineAmount = line.getAmount().abs();
    LocalDate lineDate = line.getTransactionDate();

    List<TrustTransaction> matches =
        candidates.stream()
            .filter(
                c -> {
                  if (c.getAmount().compareTo(lineAmount) != 0) return false;
                  long daysDiff =
                      Math.abs(lineDate.toEpochDay() - c.getTransactionDate().toEpochDay());
                  return daysDiff <= 3;
                })
            .toList();

    return matches.size() == 1 ? matches.getFirst() : null;
  }

  /**
   * Tier 3's outcome: below the auto-match threshold -- set confidence and suggest the transaction,
   * but remain UNMATCHED so the user can review and manually confirm the match.
   */
  private void suggest(BankStatementLine line, TrustTransaction transaction) {
    line.setMatchConfidence(new BigDecimal("0.60"));
    line.setTrustTransactionId(transaction.getId());
    bankStatementLineRepository.save(line);
  }

  private void tryAmountOnlyMatch(BankStatementLine line, List<TrustTransaction> candidates) {
    BigDecimal lineAmount = line.getAmount().abs();

    List<TrustTransaction> matches =
        candidates.stream().filter(c -> c.getAmount().compareTo(lineAmount) == 0).toList();

    if (!matches.isEmpty()) {
      line.setMatchConfidence(new BigDecimal("0.40"));
      bankStatementLineRepository.save(line);
    }
  }

  /** A line auto-match has matched to a row, applied once the row is locked (CF-3). */
  private record PlannedMatch(
      BankStatementLine line, TrustTransaction transaction, BigDecimal confidence) {}

  /** Plans {@code line} → {@code transaction} and takes the row out of the candidate pool. */
  private static void plan(
      List<PlannedMatch> planned,
      List<TrustTransaction> candidatePool,
      BankStatementLine line,
      TrustTransaction transaction,
      BigDecimal confidence) {
    planned.add(new PlannedMatch(line, transaction, confidence));
    candidatePool.removeIf(c -> c.getId().equals(transaction.getId()));
  }

  /**
   * Locks the planned row and re-reads it under the lock ({@code refresh}: the pool's unlocked copy
   * is managed, so a locking query would hand back that stale copy). A row that a concurrent
   * operation linked, or whose status changed, since the pool was read is skipped (false) and its
   * line stays {@code UNMATCHED}. Otherwise both sides are linked (true). The line is already
   * locked (step 1).
   */
  private boolean applyAutoMatch(PlannedMatch match) {
    var transaction = match.transaction();
    String statusInPool = transaction.getStatus();
    entityManager.refresh(transaction, LockModeType.PESSIMISTIC_WRITE);
    if (transaction.getBankStatementLineId() != null
        || !Objects.equals(statusInPool, transaction.getStatus())) {
      return false;
    }

    var line = match.line();
    line.setMatchStatus("AUTO_MATCHED");
    line.setTrustTransactionId(transaction.getId());
    line.setMatchConfidence(match.confidence());
    // §16.3.5 / ADR-380 (647C): auto-match runs first; a line that auto-matches keeps no
    // suggestion.
    line.setSuggestedClassification(null);
    bankStatementLineRepository.save(line);

    postingService.linkBankLine(transaction, line.getId());
    return true;
  }

  // --- Private Helpers ---

  private String readFirstLine(byte[] fileBytes) {
    try (var reader =
        new BufferedReader(
            new InputStreamReader(new ByteArrayInputStream(fileBytes), StandardCharsets.UTF_8))) {
      return reader.readLine();
    } catch (IOException e) {
      return null;
    }
  }

  private String detectFormat(BankStatementParser parser) {
    // Database check constraint only allows 'CSV' and 'OFX'
    if (parser instanceof CsvBankStatementParser) {
      return "CSV";
    }
    return "CSV";
  }

  private BankStatementResponse toResponse(BankStatement statement, List<BankStatementLine> lines) {
    // One batched lookup for the rows the statement's RECORDED lines created (647A).
    var recordedIds =
        lines.stream()
            .filter(line -> "RECORDED".equals(line.getMatchStatus()))
            .map(BankStatementLine::getId)
            .toList();
    Map<UUID, List<UUID>> created =
        recordedIds.isEmpty()
            ? Map.of()
            : trustTransactionRepository.findByBankStatementLineIdIn(recordedIds).stream()
                .collect(
                    Collectors.groupingBy(
                        TrustTransaction::getBankStatementLineId,
                        LinkedHashMap::new,
                        Collectors.mapping(TrustTransaction::getId, Collectors.toList())));
    return new BankStatementResponse(
        statement.getId(),
        statement.getTrustAccountId(),
        statement.getPeriodStart(),
        statement.getPeriodEnd(),
        statement.getOpeningBalance(),
        statement.getClosingBalance(),
        statement.getFileName(),
        statement.getFormat(),
        statement.getLineCount(),
        statement.getMatchedCount(),
        statement.getStatus(),
        statement.getImportedBy(),
        statement.getCreatedAt(),
        statement.getUpdatedAt(),
        statement.getRetiredAt(),
        statement.getRetiredBy(),
        statement.getRetiredReason(),
        statement.getReplacedByStatementId(),
        lines.stream()
            .map(
                line ->
                    BankStatementLineResponse.of(
                        line, created.getOrDefault(line.getId(), List.of())))
            .toList());
  }

  /**
   * Sanitize an uploaded filename to prevent path traversal in S3 keys. Returns "upload.csv" for
   * null/blank input, strips directory components, and replaces unsafe characters with underscores.
   */
  static String sanitizeFileName(String original) {
    if (original == null || original.isBlank()) {
      return "upload.csv";
    }
    // Strip path components (everything before the last / or \)
    int lastSlash = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
    String name = lastSlash >= 0 ? original.substring(lastSlash + 1) : original;
    // Replace characters not in the safe set with underscores
    name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
    return name.isBlank() ? "upload.csv" : name;
  }
}

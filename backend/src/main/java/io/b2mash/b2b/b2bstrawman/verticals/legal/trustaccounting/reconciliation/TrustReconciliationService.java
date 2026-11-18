package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountSection;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityFlag;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.TrustIntegrityReport;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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

  private final BankStatementRepository bankStatementRepository;
  private final BankStatementLineRepository bankStatementLineRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final TrustTransactionRepository trustTransactionRepository;
  private final TrustReconciliationRepository trustReconciliationRepository;
  private final TrustIntegrityService integrityService;
  private final TrustPostingService postingService;
  private final StorageService storageService;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final Clock clock;
  private final StatementLinePostingService statementLinePostingService;
  private final List<BankStatementParser> parsers;

  public TrustReconciliationService(
      BankStatementRepository bankStatementRepository,
      BankStatementLineRepository bankStatementLineRepository,
      TrustAccountRepository trustAccountRepository,
      TrustTransactionRepository trustTransactionRepository,
      TrustReconciliationRepository trustReconciliationRepository,
      TrustIntegrityService integrityService,
      TrustPostingService postingService,
      StorageService storageService,
      VerticalModuleGuard moduleGuard,
      AuditService auditService,
      Clock clock,
      StatementLinePostingService statementLinePostingService) {
    this.bankStatementRepository = bankStatementRepository;
    this.bankStatementLineRepository = bankStatementLineRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.trustTransactionRepository = trustTransactionRepository;
    this.trustReconciliationRepository = trustReconciliationRepository;
    this.integrityService = integrityService;
    this.postingService = postingService;
    this.storageService = storageService;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
    this.clock = clock;
    this.statementLinePostingService = statementLinePostingService;
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

  @Transactional
  public BankStatementResponse importBankStatement(UUID accountId, MultipartFile file) {
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

    // Store file in S3 only after a successful parse, so a rejected import leaves no orphaned
    // object. G88-F33: a fresh uuid segment per import, so a re-import of a same-named file (banks
    // export fixed names such as statement.csv) never overwrites the earlier raw file.
    String s3Key =
        "trust-statements/%s/%s/%s/%s"
            .formatted(tenantSchema, accountId, UUID.randomUUID(), fileName);
    storageService.upload(s3Key, fileBytes, file.getContentType());

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
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("bank_statement.imported")
            .entityType("bank_statement")
            .entityId(statement.getId())
            .details(
                Map.of(
                    "trust_account_id",
                    accountId.toString(),
                    "file_name",
                    fileName,
                    "format",
                    format,
                    "line_count",
                    parsed.lines().size()))
            .build());

    return toResponse(statement, savedLines);
  }

  @Transactional(readOnly = true)
  public List<BankStatementResponse> listBankStatements(UUID accountId) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    return bankStatementRepository.findByTrustAccountIdOrderByPeriodEndDesc(accountId).stream()
        .map(stmt -> toResponse(stmt, List.of()))
        .toList();
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

  @Transactional
  public MatchResult autoMatchStatement(UUID statementId) {
    moduleGuard.requireModule(MODULE_ID);

    var statement =
        bankStatementRepository
            .findById(statementId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatement", statementId));

    // Build candidate pool: APPROVED/RECORDED transactions, unmatched, within date range
    LocalDate startDate = statement.getPeriodStart().minusDays(7);
    LocalDate endDate = statement.getPeriodEnd().plusDays(7);
    // Wrap in ArrayList so removeIf() doesn't mutate the JPA-managed result list
    List<TrustTransaction> candidatePool =
        new java.util.ArrayList<>(
            trustTransactionRepository.findUnmatchedCandidates(
                statement.getTrustAccountId(), startDate, endDate));

    // Get all UNMATCHED lines for this statement (deterministic order for reproducible matching)
    List<BankStatementLine> unmatchedLines =
        bankStatementLineRepository
            .findByBankStatementIdAndMatchStatusOrderByTransactionDateAscIdAsc(
                statementId, "UNMATCHED");

    int autoMatched = 0;
    int alreadyMatched = statement.getMatchedCount();

    for (BankStatementLine line : unmatchedLines) {
      // Filter candidates by sign compatibility
      List<TrustTransaction> signFilteredCandidates =
          candidatePool.stream().filter(c -> isSignCompatible(line, c)).toList();

      if (signFilteredCandidates.isEmpty()) {
        continue;
      }

      // Try matching strategies in priority order
      if (tryExactReferenceMatch(line, signFilteredCandidates, statement)) {
        autoMatched++;
        // Remove matched transaction from candidate pool
        candidatePool.removeIf(c -> c.getId().equals(line.getTrustTransactionId()));
        continue;
      }

      if (tryAmountExactDateMatch(line, signFilteredCandidates, statement)) {
        autoMatched++;
        candidatePool.removeIf(c -> c.getId().equals(line.getTrustTransactionId()));
        continue;
      }

      if (tryAmountCloseDateMatch(line, signFilteredCandidates)) {
        // Confidence 0.60 -- below threshold, remains UNMATCHED
        continue;
      }

      tryAmountOnlyMatch(line, signFilteredCandidates);
    }

    // Persist updated matchedCount
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

    if (!"APPROVED".equals(transaction.getStatus())
        && !"RECORDED".equals(transaction.getStatus())) {
      throw new InvalidStateException(
          "Invalid match state", "Transaction must be APPROVED or RECORDED to match");
    }

    // Verify the line and transaction belong to the same trust account (statement locked after
    // the line: it serialises the matched_count update)
    var statement =
        bankStatementRepository
            .findByIdForUpdate(line.getBankStatementId())
            .orElseThrow(
                () -> new ResourceNotFoundException("BankStatement", line.getBankStatementId()));
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

    UUID transactionId = line.getTrustTransactionId();

    // Clear the line side
    line.setMatchStatus("UNMATCHED");
    line.setTrustTransactionId(null);
    line.setMatchConfidence(null);
    bankStatementLineRepository.save(line);

    // Clear the transaction side
    if (transactionId != null) {
      trustTransactionRepository
          .findById(transactionId)
          .ifPresent(
              txn -> {
                postingService.linkBankLine(txn, null);
              });
    }

    // Decrement matched count (statement locked after the line); refused once the statement is
    // RECONCILED (D-14) — the refusal rolls back the line and transaction changes above.
    var statement =
        bankStatementRepository
            .findByIdForUpdate(line.getBankStatementId())
            .orElseThrow(
                () -> new ResourceNotFoundException("BankStatement", line.getBankStatementId()));
    StatementLines.requireNotReconciled(statement);
    statement.decrementMatched();
    bankStatementRepository.save(statement);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("bank_statement_line.unmatched")
            .entityType("bank_statement_line")
            .entityId(lineId)
            .build());
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
   * A reconciliation (§16.4.4; {@code monthlyListId} lands in 650A). Every figure is as at {@code
   * periodEnd}. {@code bankToCashbookDifference = adjusted bank − cashbook} (EQ-1), {@code
   * cashbookToCreditorsDifference = cashbook − creditors} (EQ-2), {@code difference = adjusted bank
   * − creditors}. {@code excludedLinesTotal} (signed) and {@code unresolvedLineCount} are read live
   * from the statement's lines. {@code integrityFlags} are the flags of the last calculation (649C,
   * §16.3.4 step 5): a blocking flag stops completion.
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
      String notes,
      Instant createdAt,
      Instant updatedAt) {}

  public record CreateReconciliationRequest(
      @jakarta.validation.constraints.NotNull LocalDate periodEnd,
      @jakarta.validation.constraints.NotNull UUID bankStatementId) {}

  // --- Reconciliation Methods ---

  /**
   * Starts a reconciliation of {@code accountId} as at {@code periodEnd} — the firm's choice, never
   * derived from the statement (§16.3.4, ADR-377). The statement is required and must fit the
   * period end, so that its closing balance is the bank balance as at {@code periodEnd}: it belongs
   * to the account, has no line after {@code periodEnd}, no other imported statement of the account
   * has a line between its last line and {@code periodEnd}, and {@code periodEnd} is not in the
   * future. The first failing rule refuses (400).
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
    var statement =
        bankStatementRepository
            .findById(bankStatementId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatement", bankStatementId));
    requireStatementFitsPeriodEnd(accountId, statement, periodEnd);

    var reconciliation = new TrustReconciliation(accountId, periodEnd, bankStatementId);
    reconciliation = trustReconciliationRepository.save(reconciliation);

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

    return toReconciliationResponse(reconciliation);
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

    return trustReconciliationRepository
        .findByTrustAccountIdOrderByPeriodEndDesc(accountId)
        .stream()
        .map(this::toReconciliationResponse)
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

    return toReconciliationResponse(reconciliation);
  }

  /**
   * Recalculates a DRAFT reconciliation as at its period end (§16.3.4 steps 1–5): the statement
   * must still fit the period end; every term, {@code is_balanced} (EQ-1 and EQ-2) and the
   * integrity flags are stored.
   */
  @Transactional
  public TrustReconciliationResponse calculateReconciliation(UUID reconciliationId) {
    moduleGuard.requireModule(MODULE_ID);

    var reconciliation =
        requireDraft(reconciliationId, "Cannot recalculate a COMPLETED reconciliation");
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

    return toReconciliationResponse(reconciliation);
  }

  /**
   * Completes a DRAFT reconciliation — the gate G1–G4 (§16.3.4, ADR-377). It recalculates first and
   * never trusts stored figures (a statement imported or a posting entered since the last calculate
   * is caught here), then evaluates all four conditions and, if any fails, refuses once: the title
   * of the first failing condition in the order G1 → G4 and one sentence per failing condition in
   * the detail. Warning flags never block (a client or the Fund ledger in debit is flag-only —
   * ruling #5, OL-7, decision 9). On success the reconciliation is {@code COMPLETED}, its statement
   * {@code RECONCILED}, and the flags are stored.
   */
  @Transactional
  public TrustReconciliationResponse completeReconciliation(UUID reconciliationId) {
    moduleGuard.requireModule(MODULE_ID);

    var reconciliation = requireDraft(reconciliationId, "Reconciliation is already COMPLETED");

    // The statement row is locked BEFORE the gate inputs are read: an unmatch of one of its lines
    // locks the same row (after the line and transaction), so the two are serialised — either the
    // unmatch commits first and G4 sees the unresolved line, or this completes first and the
    // unmatch is refused on a RECONCILED statement (D-14).
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

    var details = new LinkedHashMap<String, Object>();
    details.put("trust_account_id", reconciliation.getTrustAccountId().toString());
    details.put("period_end", reconciliation.getPeriodEnd().toString());
    details.put("bank_balance", reconciliation.getBankBalance().toString());
    details.put("cashbook_balance", reconciliation.getCashbookBalance().toString());
    details.put("client_ledger_total", reconciliation.getClientLedgerTotal().toString());
    details.put("creditor_total", reconciliation.getCreditorTotal().toString());
    details.put("difference", reconciliation.getDifference().toString());
    details.put("flags", report.flags().stream().map(IntegrityFlag::code).toList());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_reconciliation.completed")
            .entityType("trust_reconciliation")
            .entityId(reconciliation.getId())
            .details(details)
            .build());

    return toReconciliationResponse(reconciliation);
  }

  private TrustReconciliation requireDraft(UUID reconciliationId, String detailWhenCompleted) {
    var reconciliation =
        trustReconciliationRepository
            .findById(reconciliationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustReconciliation", reconciliationId));
    if (reconciliation.getStatus() != ReconciliationStatus.DRAFT) {
      throw new InvalidStateException("Invalid reconciliation state", detailWhenCompleted);
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

  private TrustReconciliationResponse toReconciliationResponse(TrustReconciliation recon) {
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
        recon.getNotes(),
        recon.getCreatedAt(),
        recon.getUpdatedAt());
  }

  // --- Auto-Match Strategy Helpers ---

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

  private boolean tryExactReferenceMatch(
      BankStatementLine line, List<TrustTransaction> candidates, BankStatement statement) {
    if (line.getReference() == null || line.getReference().isBlank()) {
      return false;
    }

    List<TrustTransaction> refMatches =
        candidates.stream()
            .filter(
                c ->
                    c.getReference() != null
                        && c.getReference().equalsIgnoreCase(line.getReference())
                        && c.getAmount().compareTo(line.getAmount().abs()) == 0)
            .toList();

    if (refMatches.size() == 1) {
      applyAutoMatch(line, refMatches.getFirst(), new BigDecimal("1.00"), statement);
      return true;
    }
    return false;
  }

  private boolean tryAmountExactDateMatch(
      BankStatementLine line, List<TrustTransaction> candidates, BankStatement statement) {
    BigDecimal lineAmount = line.getAmount().abs();

    List<TrustTransaction> matches =
        candidates.stream()
            .filter(
                c ->
                    c.getAmount().compareTo(lineAmount) == 0
                        && c.getTransactionDate().equals(line.getTransactionDate()))
            .toList();

    if (matches.size() == 1) {
      applyAutoMatch(line, matches.getFirst(), AUTO_MATCH_THRESHOLD, statement);
      return true;
    }
    return false;
  }

  private boolean tryAmountCloseDateMatch(
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

    if (matches.size() == 1) {
      // Below auto-match threshold -- set confidence and suggest the transaction, but remain
      // UNMATCHED so the user can review and manually confirm the match.
      line.setMatchConfidence(new BigDecimal("0.60"));
      line.setTrustTransactionId(matches.getFirst().getId());
      bankStatementLineRepository.save(line);
      return true;
    }
    return false;
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

  private void applyAutoMatch(
      BankStatementLine line,
      TrustTransaction transaction,
      BigDecimal confidence,
      BankStatement statement) {
    // Re-fetch with pessimistic write lock to prevent concurrent match claims.
    // While auto-match on the same statement is inherently sequential (one transaction per loop
    // iteration), this guards against a concurrent manualMatch claiming the same transaction.
    var locked =
        trustTransactionRepository
            .findByIdForUpdate(transaction.getId())
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustTransaction", transaction.getId()));

    // Double-check the transaction hasn't been claimed since we built the candidate pool
    if (locked.getBankStatementLineId() != null) {
      return; // Already claimed by a concurrent operation — skip silently
    }

    line.setMatchStatus("AUTO_MATCHED");
    line.setTrustTransactionId(locked.getId());
    line.setMatchConfidence(confidence);
    // §16.3.5 / ADR-380 (647C): auto-match runs first; a line that auto-matches keeps no
    // suggestion.
    line.setSuggestedClassification(null);
    bankStatementLineRepository.save(line);

    postingService.linkBankLine(locked, line.getId());
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

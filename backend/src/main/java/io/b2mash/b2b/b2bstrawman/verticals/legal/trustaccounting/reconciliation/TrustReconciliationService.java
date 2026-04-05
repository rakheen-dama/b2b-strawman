package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.AbsaCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.BankStatementParseException;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.BankStatementParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.CsvBankStatementParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.FnbCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.GenericCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.NedbankCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.ParsedStatement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.StandardBankCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Service for bank statement import, format detection, and reconciliation operations. */
@Service
public class TrustReconciliationService {

  private static final String MODULE_ID = "trust_accounting";
  private static final BigDecimal AUTO_MATCH_THRESHOLD = new BigDecimal("0.80");

  // Note: TRANSFER_IN appears in CREDIT_TYPES because from the bank statement's perspective,
  // an inter-account transfer creates a credit on the receiving account. Transfer transactions
  // create two records (TRANSFER_OUT on source, TRANSFER_IN on destination), but each record
  // belongs to its respective trust account, so sign matching is correct per-account.
  private static final Set<String> CREDIT_TYPES =
      Set.of("DEPOSIT", "TRANSFER_IN", "INTEREST_CREDIT");
  private static final Set<String> DEBIT_TYPES =
      Set.of("PAYMENT", "TRANSFER_OUT", "FEE_TRANSFER", "REFUND", "INTEREST_LPFF");

  private final BankStatementRepository bankStatementRepository;
  private final BankStatementLineRepository bankStatementLineRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final TrustTransactionRepository trustTransactionRepository;
  private final TrustReconciliationRepository trustReconciliationRepository;
  private final ClientLedgerCardRepository clientLedgerCardRepository;
  private final StorageService storageService;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final List<BankStatementParser> parsers;

  public TrustReconciliationService(
      BankStatementRepository bankStatementRepository,
      BankStatementLineRepository bankStatementLineRepository,
      TrustAccountRepository trustAccountRepository,
      TrustTransactionRepository trustTransactionRepository,
      TrustReconciliationRepository trustReconciliationRepository,
      ClientLedgerCardRepository clientLedgerCardRepository,
      StorageService storageService,
      VerticalModuleGuard moduleGuard,
      AuditService auditService) {
    this.bankStatementRepository = bankStatementRepository;
    this.bankStatementLineRepository = bankStatementLineRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.trustTransactionRepository = trustTransactionRepository;
    this.trustReconciliationRepository = trustReconciliationRepository;
    this.clientLedgerCardRepository = clientLedgerCardRepository;
    this.storageService = storageService;
    this.moduleGuard = moduleGuard;
    this.auditService = auditService;
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
      BigDecimal matchConfidence) {}

  // --- Service Methods ---

  @Transactional
  public BankStatementResponse importBankStatement(UUID accountId, MultipartFile file) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    String tenantSchema = RequestScopes.requireTenantId();
    UUID memberId = RequestScopes.requireMemberId();
    String fileName = sanitizeFileName(file.getOriginalFilename());

    // Read file bytes so we can use them for both S3 upload and parsing
    byte[] fileBytes;
    try {
      fileBytes = file.getBytes();
    } catch (IOException e) {
      throw new BankStatementParseException("Failed to read uploaded file", e);
    }

    // Store file in S3
    String s3Key = "trust-statements/" + tenantSchema + "/" + accountId + "/" + fileName;
    storageService.upload(s3Key, fileBytes, file.getContentType());

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

    var line =
        bankStatementLineRepository
            .findById(lineId)
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

    // Verify the line and transaction belong to the same trust account
    var statement =
        bankStatementRepository
            .findById(line.getBankStatementId())
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

    transaction.setBankStatementLineId(lineId);
    trustTransactionRepository.save(transaction);

    // Update matched count (statement already loaded above for trust account validation)
    statement.setMatchedCount(statement.getMatchedCount() + 1);
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

    var line =
        bankStatementLineRepository
            .findById(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatementLine", lineId));

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
                txn.setBankStatementLineId(null);
                trustTransactionRepository.save(txn);
              });
    }

    // Decrement matched count
    var statement =
        bankStatementRepository
            .findById(line.getBankStatementId())
            .orElseThrow(
                () -> new ResourceNotFoundException("BankStatement", line.getBankStatementId()));
    statement.setMatchedCount(Math.max(0, statement.getMatchedCount() - 1));
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

    var line =
        bankStatementLineRepository
            .findById(lineId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatementLine", lineId));

    if (!"UNMATCHED".equals(line.getMatchStatus())) {
      throw new InvalidStateException(
          "Invalid match state", "Only UNMATCHED lines can be excluded");
    }

    // Intentionally does NOT update matchedCount: excluded lines are not "matched" in the
    // reconciliation sense. matchedCount tracks only AUTO_MATCHED + MANUALLY_MATCHED lines,
    // so the reconciliation progress percentage remains accurate.
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

  public record TrustReconciliationResponse(
      UUID id,
      UUID trustAccountId,
      LocalDate periodEnd,
      UUID bankStatementId,
      BigDecimal bankBalance,
      BigDecimal cashbookBalance,
      BigDecimal clientLedgerTotal,
      BigDecimal outstandingDeposits,
      BigDecimal outstandingPayments,
      BigDecimal adjustedBankBalance,
      boolean isBalanced,
      String status,
      UUID completedBy,
      Instant completedAt,
      String notes,
      Instant createdAt,
      Instant updatedAt) {}

  public record CreateReconciliationRequest(
      @jakarta.validation.constraints.NotNull LocalDate periodEnd, UUID bankStatementId) {}

  // --- Reconciliation Methods ---

  @Transactional
  public TrustReconciliationResponse createReconciliation(
      UUID accountId, LocalDate periodEnd, UUID bankStatementId) {
    moduleGuard.requireModule(MODULE_ID);

    trustAccountRepository
        .findById(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("TrustAccount", accountId));

    if (bankStatementId != null) {
      var statement =
          bankStatementRepository
              .findById(bankStatementId)
              .orElseThrow(() -> new ResourceNotFoundException("BankStatement", bankStatementId));
      if (!statement.getTrustAccountId().equals(accountId)) {
        throw new InvalidStateException(
            "Trust account mismatch",
            "Bank statement does not belong to the specified trust account");
      }
    }

    var reconciliation = new TrustReconciliation(accountId, periodEnd, bankStatementId);
    reconciliation = trustReconciliationRepository.save(reconciliation);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_reconciliation.created")
            .entityType("trust_reconciliation")
            .entityId(reconciliation.getId())
            .details(
                Map.of(
                    "trust_account_id", accountId.toString(), "period_end", periodEnd.toString()))
            .build());

    return toReconciliationResponse(reconciliation);
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

  @Transactional
  public TrustReconciliationResponse calculateReconciliation(UUID reconciliationId) {
    moduleGuard.requireModule(MODULE_ID);

    var reconciliation =
        trustReconciliationRepository
            .findById(reconciliationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustReconciliation", reconciliationId));

    if (reconciliation.getStatus() != ReconciliationStatus.DRAFT) {
      throw new InvalidStateException(
          "Invalid reconciliation state", "Cannot recalculate a COMPLETED reconciliation");
    }

    UUID bankStatementId = reconciliation.getBankStatementId();
    if (bankStatementId == null) {
      throw new InvalidStateException(
          "Missing bank statement",
          "A bank statement must be linked to calculate the reconciliation");
    }

    var bankStatement =
        bankStatementRepository
            .findById(bankStatementId)
            .orElseThrow(() -> new ResourceNotFoundException("BankStatement", bankStatementId));

    UUID accountId = reconciliation.getTrustAccountId();
    BigDecimal bankBalance = bankStatement.getClosingBalance();
    BigDecimal cashbookBalance = trustTransactionRepository.calculateCashbookBalance(accountId);
    BigDecimal clientLedgerTotal = clientLedgerCardRepository.calculateTotalTrustBalance(accountId);
    BigDecimal outstandingDeposits =
        trustTransactionRepository.calculateOutstandingDeposits(accountId);
    BigDecimal outstandingPayments =
        trustTransactionRepository.calculateOutstandingPayments(accountId);
    BigDecimal adjustedBankBalance =
        bankBalance.add(outstandingDeposits).subtract(outstandingPayments);

    boolean isBalanced =
        adjustedBankBalance.compareTo(cashbookBalance) == 0
            && cashbookBalance.compareTo(clientLedgerTotal) == 0;

    reconciliation.setBankBalance(bankBalance);
    reconciliation.setCashbookBalance(cashbookBalance);
    reconciliation.setClientLedgerTotal(clientLedgerTotal);
    reconciliation.setOutstandingDeposits(outstandingDeposits);
    reconciliation.setOutstandingPayments(outstandingPayments);
    reconciliation.setAdjustedBankBalance(adjustedBankBalance);
    reconciliation.setBalanced(isBalanced);

    reconciliation = trustReconciliationRepository.save(reconciliation);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_reconciliation.calculated")
            .entityType("trust_reconciliation")
            .entityId(reconciliation.getId())
            .details(
                Map.of(
                    "trust_account_id",
                    accountId.toString(),
                    "bank_balance",
                    bankBalance.toString(),
                    "cashbook_balance",
                    cashbookBalance.toString(),
                    "client_ledger_total",
                    clientLedgerTotal.toString(),
                    "is_balanced",
                    isBalanced))
            .build());

    return toReconciliationResponse(reconciliation);
  }

  @Transactional
  public TrustReconciliationResponse completeReconciliation(UUID reconciliationId) {
    moduleGuard.requireModule(MODULE_ID);

    var reconciliation =
        trustReconciliationRepository
            .findById(reconciliationId)
            .orElseThrow(
                () -> new ResourceNotFoundException("TrustReconciliation", reconciliationId));

    if (reconciliation.getStatus() != ReconciliationStatus.DRAFT) {
      throw new InvalidStateException(
          "Invalid reconciliation state", "Reconciliation is already COMPLETED");
    }

    if (!reconciliation.isBalanced()) {
      throw new InvalidStateException(
          "Reconciliation not balanced",
          "Cannot complete reconciliation: adjustedBankBalance=%s, cashbookBalance=%s, clientLedgerTotal=%s"
              .formatted(
                  reconciliation.getAdjustedBankBalance(),
                  reconciliation.getCashbookBalance(),
                  reconciliation.getClientLedgerTotal()));
    }

    reconciliation.setStatus(ReconciliationStatus.COMPLETED);
    reconciliation.setCompletedBy(RequestScopes.requireMemberId());
    reconciliation.setCompletedAt(Instant.now());

    reconciliation = trustReconciliationRepository.save(reconciliation);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("trust_reconciliation.completed")
            .entityType("trust_reconciliation")
            .entityId(reconciliation.getId())
            .details(
                Map.of(
                    "trust_account_id",
                    reconciliation.getTrustAccountId().toString(),
                    "period_end",
                    reconciliation.getPeriodEnd().toString(),
                    "bank_balance",
                    reconciliation.getBankBalance().toString(),
                    "cashbook_balance",
                    reconciliation.getCashbookBalance().toString(),
                    "client_ledger_total",
                    reconciliation.getClientLedgerTotal().toString()))
            .build());

    return toReconciliationResponse(reconciliation);
  }

  private TrustReconciliationResponse toReconciliationResponse(TrustReconciliation recon) {
    return new TrustReconciliationResponse(
        recon.getId(),
        recon.getTrustAccountId(),
        recon.getPeriodEnd(),
        recon.getBankStatementId(),
        recon.getBankBalance(),
        recon.getCashbookBalance(),
        recon.getClientLedgerTotal(),
        recon.getOutstandingDeposits(),
        recon.getOutstandingPayments(),
        recon.getAdjustedBankBalance(),
        recon.isBalanced(),
        recon.getStatus().name(),
        recon.getCompletedBy(),
        recon.getCompletedAt(),
        recon.getNotes(),
        recon.getCreatedAt(),
        recon.getUpdatedAt());
  }

  // --- Auto-Match Strategy Helpers ---

  private boolean isSignCompatible(BankStatementLine line, TrustTransaction candidate) {
    boolean lineIsCredit = line.getAmount().compareTo(BigDecimal.ZERO) > 0;
    if (lineIsCredit) {
      return CREDIT_TYPES.contains(candidate.getTransactionType());
    } else {
      return DEBIT_TYPES.contains(candidate.getTransactionType());
    }
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
    bankStatementLineRepository.save(line);

    locked.setBankStatementLineId(line.getId());
    trustTransactionRepository.save(locked);
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
        lines.stream().map(this::toLineResponse).toList());
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

  private BankStatementLineResponse toLineResponse(BankStatementLine line) {
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
        line.getMatchConfidence());
  }
}

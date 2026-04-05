package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.AbsaCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.BankStatementParseException;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.BankStatementParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.CsvBankStatementParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.FnbCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.GenericCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.NedbankCsvParser;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.ParsedStatement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser.StandardBankCsvParser;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Service for bank statement import, format detection, and reconciliation operations. */
@Service
public class TrustReconciliationService {

  private static final String MODULE_ID = "trust_accounting";

  private final BankStatementRepository bankStatementRepository;
  private final BankStatementLineRepository bankStatementLineRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final StorageService storageService;
  private final VerticalModuleGuard moduleGuard;
  private final AuditService auditService;
  private final List<BankStatementParser> parsers;

  public TrustReconciliationService(
      BankStatementRepository bankStatementRepository,
      BankStatementLineRepository bankStatementLineRepository,
      TrustAccountRepository trustAccountRepository,
      StorageService storageService,
      VerticalModuleGuard moduleGuard,
      AuditService auditService) {
    this.bankStatementRepository = bankStatementRepository;
    this.bankStatementLineRepository = bankStatementLineRepository;
    this.trustAccountRepository = trustAccountRepository;
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
    String fileName =
        file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.csv";

    // Read file bytes so we can use them for both S3 upload and parsing
    byte[] fileBytes;
    try {
      fileBytes = file.getBytes();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read uploaded file", e);
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

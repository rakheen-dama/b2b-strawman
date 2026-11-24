package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.takeon.dataset.TrustPositionEquation.TrustEquationSummary;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.CreditorLine;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListService.CreditorList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The take-on report of a committed trust take-on (Phase 93 657C, §17.3.12, ADR-391, R-9): what the
 * PDF and CSV print (657D), built inside the commit transaction after the self-check, so it can
 * never disagree with the books it describes. Stored once as {@code take_on_reports.snapshot}
 * ({@link #toMap()}) with the SHA-256 of its canonical JSON ({@link #sha256}).
 *
 * <p>Every amount is a two-decimal string and every date an ISO {@code yyyy-MM-dd} string (R-8):
 * Hibernate's tenant JSON mapper has no {@code java.time} support, and a jsonb number would come
 * back as a double — so the stored map reads back exactly as written and its hash recomputes.
 *
 * <p>{@code previousSystem} is the reconciled position as entered; {@code kazi} the same figures
 * read from the books at the cutover date in the commit transaction; {@code differences} {@code
 * kazi − previousSystem} (every one R0,00 by the self-check). Kazi holds no bank statement at the
 * cutover date, so its bank line is the entered one. {@code creditorList} is {@code
 * TrustMonthlyListService.liveList(G, C)} — the same code a month-end lock freezes — and {@code
 * investmentAccounts} the same for each investment account the batch names. No FICA attestation:
 * that belongs to the clients batch (§17.3.12).
 */
public record TakeOnReportSnapshot(
    FirmHeader firm,
    AccountHeader account,
    String cutoverDate,
    boolean midMonth,
    ReconciledPosition previousSystem,
    ReconciledPosition kazi,
    ReconciledPosition differences,
    CreditorListSection creditorList,
    List<CreditorListSection> investmentAccounts,
    List<InvestmentLine> investments,
    List<UnidentifiedLine> unidentified,
    List<OutstandingLine> outstandingItems,
    TrustEquationSummary totals,
    List<String> flags,
    SignOffSection signOff,
    List<SourceFile> sources,
    UUID batchId) {

  /** The line printed when the preparer is the signer (FD-31). */
  public static final String SAME_PERSON = "Prepared and signed off by the same person";

  /** The line printed on a dual-approval account: the sign-off authorised every row (FD-25). */
  public static final String AUTHORISED_BY_SIGN_OFF = "Authorised by the take-on sign-off";

  public static final String MID_MONTH_CUTOVER = "MID_MONTH_CUTOVER";

  public record FirmHeader(String name) {}

  public record AccountHeader(
      UUID id,
      String name,
      String accountNumber,
      String bankName,
      String accountType,
      boolean requireDualApproval) {}

  /** The reconciled position at the cutover date (EQ-1 terms, the Fund, the total invested). */
  public record ReconciledPosition(
      String bankBalance,
      String outstandingDeposits,
      String outstandingPayments,
      String adjustedBank,
      String cashbook,
      String fundLedger,
      String investedTotal) {}

  /** One account's Rule 54.15-style creditor list at the cutover date ({@code liveList}). */
  public record CreditorListSection(
      UUID trustAccountId,
      String accountName,
      String asAt,
      String cashbook,
      String creditorTotal,
      List<CreditorLineEntry> lines) {}

  /** {@link CreditorLine} with its amounts as two-decimal strings. */
  public record CreditorLineEntry(
      String kind,
      UUID clientId,
      String name,
      List<MatterEntry> matters,
      String balance,
      UUID unidentifiedDepositId,
      String receivedDate,
      String bankDescription,
      String totalDeposits,
      String totalPayments,
      String totalFeeTransfers,
      String lastTransactionDate,
      String broughtForward) {}

  public record MatterEntry(UUID matterId, String name, String balance) {}

  /**
   * An investment taken on: basis, client and matter (section 86(4) only), where it is held, the
   * figures at cutover (accrued interest is a memo, never posted — FD-13), and the instruction.
   */
  public record InvestmentLine(
      String basis,
      UUID clientId,
      String clientName,
      UUID matterId,
      String matterName,
      UUID investmentAccountId,
      String investmentAccountName,
      String institution,
      String accountNumber,
      String principal,
      String interestRatePercent,
      String depositDate,
      String accruedInterest,
      String fundAmount,
      String balancePerConfirmation,
      String instructionReference,
      String instructionDate,
      boolean endorsementConfirmed) {}

  /** A held unidentified deposit: its original received date and its age at the cutover date. */
  public record UnidentifiedLine(
      String receivedDate, long ageInDaysAtCutover, String amount, String reference) {}

  /** An item outstanding at cutover (a deposit in transit or a payment not yet presented). */
  public record OutstandingLine(
      String kind,
      String originalDate,
      String reference,
      UUID clientId,
      String clientName,
      UUID matterId,
      String matterName,
      String amount) {}

  /**
   * The sign-off: who, as what, the declaration and its version, when; who prepared the batch;
   * {@code notes} holds {@link #SAME_PERSON} and/or {@link #AUTHORISED_BY_SIGN_OFF}.
   */
  public record SignOffSection(
      String signedBy,
      String role,
      String statementVersion,
      String statementText,
      String signedAt,
      String preparedBy,
      boolean samePerson,
      List<String> notes) {}

  /** One source file: name, section, sheet, SHA-256, rows, who uploaded it and when. */
  public record SourceFile(
      String fileName,
      String section,
      String sheetName,
      String sha256,
      int rowCount,
      String uploadedBy,
      String uploadedAt) {}

  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** The {@code TakeOnHashes} canonical form: map keys sorted, no whitespace. */
  private static final JsonMapper CANONICAL =
      JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

  /** The jsonb shape stored in {@code take_on_reports.snapshot}. */
  @SuppressWarnings("unchecked")
  public Map<String, Object> toMap() {
    return JSON.convertValue(this, Map.class);
  }

  /**
   * SHA-256 (64 lower-case hex) of the canonical JSON of a stored snapshot map — recomputable from
   * the jsonb as read back, because the map holds only strings, booleans, integers and nulls.
   */
  public static String sha256(Map<String, Object> snapshot) {
    try {
      byte[] json = CANONICAL.writeValueAsString(snapshot).getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** {@code 1234.5} → {@code "1234.50"}; null stays null (never rounds: cents only, R-8). */
  public static String amount(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }

  /** One account's {@code liveList} as a report section. */
  public static CreditorListSection section(String accountName, CreditorList list) {
    return new CreditorListSection(
        list.trustAccountId(),
        accountName,
        list.asAt().toString(),
        amount(list.position().cashbook()),
        amount(list.position().creditorTotal()),
        list.lines().stream().map(TakeOnReportSnapshot::line).toList());
  }

  /** A {@code liveList} line as stored in the snapshot. */
  public static CreditorLineEntry line(CreditorLine line) {
    return new CreditorLineEntry(
        line.kind().name(),
        line.clientId(),
        line.name(),
        line.matters() == null
            ? List.of()
            : line.matters().stream()
                .map(m -> new MatterEntry(m.matterId(), m.name(), amount(m.balance())))
                .toList(),
        amount(line.balance()),
        line.unidentifiedDepositId(),
        line.receivedDate(),
        line.bankDescription(),
        amount(line.totalDeposits()),
        amount(line.totalPayments()),
        amount(line.totalFeeTransfers()),
        line.lastTransactionDate(),
        amount(line.broughtForward()));
  }
}

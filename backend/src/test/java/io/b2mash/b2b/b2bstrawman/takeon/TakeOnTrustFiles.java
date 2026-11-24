package io.b2mash.b2b.b2bstrawman.takeon;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trust position take-on files for integration tests (Phase 93 657B): one header and one mapping
 * per section, and the architecture §17.3.13 set (clients A/A1, B/B1, C/C1; the Fund R1 250; two
 * unidentified deposits; a section 86(3) and a section 86(4) investment; a deposit in transit and
 * an unpresented cheque), so each test states only the rows or figures it changes. Fictional data.
 */
public final class TakeOnTrustFiles {

  public static final String CLIENT_LEDGER = "CLIENT_LEDGER";
  public static final String UNIDENTIFIED_DEPOSITS = "UNIDENTIFIED_DEPOSITS";
  public static final String INVESTMENTS = "INVESTMENTS";
  public static final String OUTSTANDING_ITEMS = "OUTSTANDING_ITEMS";

  /** The sections in upload order. */
  public static final List<String> SECTIONS =
      List.of(CLIENT_LEDGER, UNIDENTIFIED_DEPOSITS, INVESTMENTS, OUTSTANDING_ITEMS);

  private static final Map<String, String> HEADERS =
      Map.of(
          CLIENT_LEDGER, "Client reference,Matter reference,Balance at cutover,Previous ID",
          UNIDENTIFIED_DEPOSITS, "Date received,Amount,Bank reference,Description",
          INVESTMENTS,
              "Investment account,Basis,Institution,Account number,Principal,Rate,Deposited,"
                  + "Client,Matter,Instruction,Instruction date,Endorsed,Accrued,Fund amount,"
                  + "Confirmed",
          OUTSTANDING_ITEMS,
              "Item type,Original date,Amount,Reference,Client reference,Matter reference");

  private static final Map<String, Map<String, String>> TARGETS =
      Map.of(
          CLIENT_LEDGER,
          Map.of(
              "clientRef", "Client reference",
              "matterRef", "Matter reference",
              "balance", "Balance at cutover",
              "sourceRef", "Previous ID"),
          UNIDENTIFIED_DEPOSITS,
          Map.of(
              "receivedDate", "Date received",
              "amount", "Amount",
              "bankReference", "Bank reference",
              "description", "Description"),
          INVESTMENTS,
          Map.ofEntries(
              Map.entry("investmentAccount", "Investment account"),
              Map.entry("basis", "Basis"),
              Map.entry("institution", "Institution"),
              Map.entry("accountNumber", "Account number"),
              Map.entry("principal", "Principal"),
              Map.entry("interestRate", "Rate"),
              Map.entry("depositDate", "Deposited"),
              Map.entry("clientRef", "Client"),
              Map.entry("matterRef", "Matter"),
              Map.entry("instructionReference", "Instruction"),
              Map.entry("instructionDate", "Instruction date"),
              Map.entry("endorsementConfirmed", "Endorsed"),
              Map.entry("accruedInterest", "Accrued"),
              Map.entry("fundAmount", "Fund amount"),
              Map.entry("balancePerConfirmation", "Confirmed")),
          OUTSTANDING_ITEMS,
          Map.of(
              "kind", "Item type",
              "originalDate", "Original date",
              "amount", "Amount",
              "reference", "Reference",
              "clientRef", "Client reference",
              "matterRef", "Matter reference"));

  private TakeOnTrustFiles() {}

  /** The mapping of {@code section}: every column mapped. */
  public static Map<String, Object> mapping(String section) {
    var body = new HashMap<String, Object>();
    body.put("targets", TARGETS.get(section));
    return body;
  }

  /** A section's CSV: its header and the rows (CSV lines without the newline). */
  public static String csv(String section, List<String> rows) {
    var sb = new StringBuilder(HEADERS.get(section)).append('\n');
    rows.forEach(r -> sb.append(r).append('\n'));
    return sb.toString();
  }

  /**
   * {@code section}'s mapping with column headers renamed ({@code renames}: header → new header),
   * for a file whose columns the firm named otherwise.
   */
  public static Map<String, Object> mapping(String section, Map<String, String> renames) {
    var targets = new HashMap<String, String>();
    TARGETS
        .get(section)
        .forEach((field, header) -> targets.put(field, renames.getOrDefault(header, header)));
    var body = new HashMap<String, Object>();
    body.put("targets", targets);
    return body;
  }

  /** {@code section}'s CSV with column headers renamed ({@code renames}: header → new header). */
  public static String csv(String section, List<String> rows, Map<String, String> renames) {
    var header = new ArrayList<String>();
    for (String h : HEADERS.get(section).split(",")) {
      header.add(renames.getOrDefault(h, h));
    }
    var sb = new StringBuilder(String.join(",", header)).append('\n');
    rows.forEach(r -> sb.append(r).append('\n'));
    return sb.toString();
  }

  /** The §17.3.13 client ledgers: A/A1 R300 000, B/B1 R150 000, C/C1 R49 500. */
  public static List<String> section17313ClientLedgers() {
    return List.of("A,A1,300000.00,", "B,B1,150000.00,", "C,C1,49500.00,");
  }

  /** The §17.3.13 unidentified deposits: R2 000 on 15 June 2025, R750 on 3 September 2026. */
  public static List<String> section17313Unidentified() {
    return List.of("15/06/2025,2000.00,ACB CREDIT UNKNOWN,", "03/09/2026,750.00,EFT 7781,");
  }

  /** An s86(3) row: the firm's own placement (no client). */
  public static String section863(String account, String principal, String confirmed) {
    return account + ",S86(3),Investec,0001," + principal + ",7.25%,01/03/2026,,,,,,,," + confirmed;
  }

  /** An s86(4) row for client A on A1 with the instruction (FD-14). */
  public static String section864(String account, String instruction) {
    return account
        + ",S86(4),Investec,0002,200000.00,7.50%,01/04/2026,A,A1,"
        + instruction
        + ",01/04/2026,Yes,1234.56,0.00,200000.00";
  }

  /**
   * The §17.3.13 investments: R100 000 s86(3) at {@code i3}, R200 000 s86(4) for A at {@code i4}.
   */
  public static List<String> section17313Investments(String i3, String i4) {
    return List.of(section863(i3, "100000.00", "100000.00"), section864(i4, "INSTR-A-01"));
  }

  /**
   * The §17.3.13 items outstanding at cutover: EFT B-0001 R5 000 (B/B1), PAY-778 R20 000 (A/A1).
   */
  public static List<String> section17313Outstanding() {
    return List.of(
        "Deposit,30/10/2026,5000.00,EFT B-0001,B,B1", "Cheque,28/10/2026,20000.00,PAY-778,A,A1");
  }

  /** The four §17.3.13 files, section → rows. */
  public static Map<String, List<String>> section17313(String i3, String i4) {
    var files = new LinkedHashMap<String, List<String>>();
    files.put(CLIENT_LEDGER, section17313ClientLedgers());
    files.put(UNIDENTIFIED_DEPOSITS, section17313Unidentified());
    files.put(INVESTMENTS, section17313Investments(i3, i4));
    files.put(OUTSTANDING_ITEMS, section17313Outstanding());
    return files;
  }

  /** A copy of {@code files} with {@code section}'s rows replaced. */
  public static Map<String, List<String>> withRows(
      Map<String, List<String>> files, String section, List<String> rows) {
    var copy = new LinkedHashMap<>(files);
    copy.put(section, new ArrayList<>(rows));
    return copy;
  }

  /** The §17.3.13 reconciled position at cutover as a {@code PUT …/position} body. */
  public static Map<String, Object> section17313Position(String cutoverDate) {
    var body = new LinkedHashMap<String, Object>();
    body.put("cutoverDate", cutoverDate);
    body.put("bankBalance", new BigDecimal("418500.00"));
    body.put("outstandingDepositsTotal", new BigDecimal("5000.00"));
    body.put("outstandingPaymentsTotal", new BigDecimal("20000.00"));
    body.put("cashbookBalance", new BigDecimal("403500.00"));
    body.put("fundLedgerBalance", new BigDecimal("1250.00"));
    body.put("investedTotal", new BigDecimal("300000.00"));
    body.put("midMonthAcknowledged", false);
    return body;
  }

  /** A copy of {@code position} with {@code key} set to {@code value}. */
  public static Map<String, Object> withFigure(
      Map<String, Object> position, String key, Object value) {
    var copy = new LinkedHashMap<>(position);
    copy.put(key, value);
    return copy;
  }
}

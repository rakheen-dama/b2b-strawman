package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Debtor opening-balances take-on files for integration tests (Phase 93 658A): one header, one
 * mapping, and a row builder, so each test states only the values it is about. Dates are {@code
 * dd/MM/yyyy}, amounts point-decimal (the default parse options). Fictional data only.
 */
public final class TakeOnDebtorsFiles {

  public static final String HEADER =
      "Client,Matter,Fee note number,Issue date,Due date,Outstanding,Currency,Description,"
          + "Previous ID,Ledger code\n";

  private static final Map<String, String> TARGETS =
      Map.ofEntries(
          Map.entry("clientRef", "Client"),
          Map.entry("matterRef", "Matter"),
          Map.entry("feeNoteNumber", "Fee note number"),
          Map.entry("issueDate", "Issue date"),
          Map.entry("dueDate", "Due date"),
          Map.entry("outstandingAmount", "Outstanding"),
          Map.entry("currency", "Currency"),
          Map.entry("description", "Description"),
          Map.entry("sourceRef", "Previous ID"));

  private TakeOnDebtorsFiles() {}

  /** The mapping every debtors test uses: all columns mapped, "Ledger code" parked. */
  public static Map<String, Object> mapping() {
    var body = new HashMap<String, Object>();
    body.put("targets", TARGETS);
    body.put("parked", List.of("Ledger code"));
    return body;
  }

  /** One fee-note row for the client {@code clientRef} with the number {@code number}. */
  public static Row row(String clientRef, String number) {
    return new Row(clientRef, number);
  }

  /** A row under construction; {@link #toString()} renders it as one CSV line. */
  public static final class Row {
    private final String clientRef;
    private final String number;
    private String matter = "";
    private String issue = "15/09/2026";
    private String due = "";
    private String amount = "1000.00";
    private String currency = "";
    private String description = "";
    private String ref = "";
    private String ledger = "";

    private Row(String clientRef, String number) {
      this.clientRef = clientRef;
      this.number = number;
    }

    public Row matter(String v) {
      this.matter = v;
      return this;
    }

    /** The issue date, {@code dd/MM/yyyy} (default 15/09/2026). */
    public Row issue(String v) {
      this.issue = v;
      return this;
    }

    public Row due(String v) {
      this.due = v;
      return this;
    }

    /** The outstanding amount (default 1000.00). */
    public Row amount(String v) {
      this.amount = v;
      return this;
    }

    public Row currency(String v) {
      this.currency = v;
      return this;
    }

    public Row description(String v) {
      this.description = v;
      return this;
    }

    /** The fee note's own previous-system ID. */
    public Row ref(String v) {
      this.ref = v;
      return this;
    }

    public Row ledger(String v) {
      this.ledger = v;
      return this;
    }

    @Override
    public String toString() {
      return Stream.of(
                  Objects.toString(clientRef, ""),
                  matter,
                  Objects.toString(number, ""),
                  issue,
                  due,
                  amount,
                  currency,
                  description,
                  ref,
                  ledger)
              .map(TakeOnClientsFiles::field)
              .collect(Collectors.joining(","))
          + "\n";
    }
  }

  /** The file: the header and the rows. */
  public static String csv(Row... rows) {
    var sb = new StringBuilder(HEADER);
    for (Row r : rows) {
      sb.append(r);
    }
    return sb.toString();
  }
}

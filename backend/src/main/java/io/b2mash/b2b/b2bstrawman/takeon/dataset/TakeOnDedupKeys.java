package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import java.util.Locale;
import java.util.UUID;

/**
 * The one definition of a take-on row's lookup-free dedup key (D-19, C-25): the dataset's key
 * field, lower-cased and prefixed ({@code "email:" + lower(email)} for clients). The dry run
 * ({@link GenericTakeOnDatasetHandler#dedupKey}) writes it to {@code take_on_rows.dedup_key}, and
 * client anonymisation ({@code DataAnonymizationService}) finds a client's unlinked rows by it — so
 * both must derive it here, never from a copy (POPIA, §17.6.5).
 */
public final class TakeOnDedupKeys {

  private TakeOnDedupKeys() {}

  /** The field whose value is the dataset's dedup key, or {@code null} when it has none. */
  static String field(TakeOnDataset dataset) {
    return switch (dataset) {
      case CLIENTS -> "email";
      case MATTERS -> "referenceNumber";
      case DEBTOR_BALANCES -> "feeNoteNumber";
      case TRUST_POSITION -> null; // needs resolved client/matter/account ids — 657B
    };
  }

  /** The dedup key of {@code value} (the dataset's key field) in {@code dataset}. */
  public static String of(TakeOnDataset dataset, String value) {
    return prefix(dataset) + value.toLowerCase(Locale.ROOT);
  }

  /** The clients dedup key of an email address. */
  public static String client(String email) {
    return of(TakeOnDataset.CLIENTS, email);
  }

  /**
   * The matters dedup key of a row without a matter reference (656A, §17.3.6): the matter's name on
   * its resolved client, {@code "name:" + clientId + ":" + lower(strip(name))}.
   */
  public static String matterName(UUID clientId, String name) {
    return "name:" + clientId + ":" + name.strip().toLowerCase(Locale.ROOT);
  }

  // --- Trust position (657B, §17.2.3): keys carry the account and the section ---

  /** {@code {account}:CLIENT_LEDGER:{clientId}:{matterId}} — both resolved (no null encoding). */
  public static String trustClientLedger(UUID accountId, UUID clientId, UUID matterId) {
    return accountId + ":CLIENT_LEDGER:" + clientId + ":" + matterId;
  }

  /**
   * {@code {account}:UNIDENTIFIED_DEPOSITS:src:{sourceRef}} when the row gives the previous
   * system's id, else {@code {account}:UNIDENTIFIED_DEPOSITS:row:{batchId}:{rowNumber}}.
   */
  public static String trustUnidentified(
      UUID accountId, String sourceRef, UUID batchId, int rowNumber) {
    return accountId + ":UNIDENTIFIED_DEPOSITS:" + sourceOrRow(sourceRef, batchId, rowNumber);
  }

  /** {@code {investmentAccount}:INVESTMENTS:{institution}:{accountNumber}}. */
  public static String trustInvestment(
      UUID investmentAccountId, String institution, String accountNumber) {
    return investmentAccountId + ":INVESTMENTS:" + lower(institution) + ":" + lower(accountNumber);
  }

  /**
   * {@code {account}:OUTSTANDING_ITEMS:{kind}:src:{sourceRef}}, else {@code
   * {account}:OUTSTANDING_ITEMS:{kind}:row:{batchId}:{rowNumber}}.
   */
  public static String trustOutstanding(
      UUID accountId, String kind, String sourceRef, UUID batchId, int rowNumber) {
    return accountId
        + ":OUTSTANDING_ITEMS:"
        + kind
        + ":"
        + sourceOrRow(sourceRef, batchId, rowNumber);
  }

  private static String sourceOrRow(String sourceRef, UUID batchId, int rowNumber) {
    return sourceRef != null && !sourceRef.isBlank()
        ? "src:" + lower(sourceRef)
        : "row:" + batchId + ":" + rowNumber;
  }

  private static String lower(String value) {
    return value.strip().toLowerCase(Locale.ROOT);
  }

  private static String prefix(TakeOnDataset dataset) {
    return switch (dataset) {
      case CLIENTS -> "email:";
      case MATTERS -> "ref:";
      case DEBTOR_BALANCES -> "number:";
      case TRUST_POSITION -> "";
    };
  }
}

package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * What a trust take-on posts (Phase 93 657C, §17.3.7, §17.3.8, §3.3 of the slice brief): the
 * batch's validated rows with every reference re-resolved under the commit's locks ({@link
 * TrustOpeningPositionPlanner}), in typed form, and the batch's Fund ledger balance. Everything is
 * dated at the cutover date on the banking account {@code G}, or on the investment account an
 * investment row names. {@link #contribution} is what the plan adds to each account's books — the
 * self-check's expected figures ({@link TrustOpeningSelfCheck}).
 */
public record TrustOpeningPlan(
    UUID bankingAccountId,
    LocalDate cutoverDate,
    BigDecimal fundLedger,
    List<ClientBalance> clientBalances,
    List<UnidentifiedItem> unidentifiedItems,
    List<Investment> investments,
    List<OutstandingItem> outstandingItems) {

  public TrustOpeningPlan {
    Objects.requireNonNull(bankingAccountId, "bankingAccountId");
    Objects.requireNonNull(cutoverDate, "cutoverDate");
    fundLedger = fundLedger == null ? BigDecimal.ZERO : fundLedger;
    clientBalances = List.copyOf(clientBalances);
    unidentifiedItems = List.copyOf(unidentifiedItems);
    investments = List.copyOf(investments);
    outstandingItems = List.copyOf(outstandingItems);
  }

  /** A client lock anchor ({@code TrustPostingService.lockClient}); taken in this order. */
  public record ClientAnchor(UUID accountId, UUID clientId) implements Comparable<ClientAnchor> {
    private static final Comparator<ClientAnchor> ORDER =
        Comparator.comparing(ClientAnchor::accountId).thenComparing(ClientAnchor::clientId);

    @Override
    public int compareTo(ClientAnchor other) {
      return ORDER.compare(this, other);
    }
  }

  /** A client and matter balance at cutover (≥ 0; nil → nothing posted, FD-33). */
  public record ClientBalance(
      TakeOnRow row,
      UUID clientId,
      String clientName,
      UUID matterId,
      String matterName,
      BigDecimal balance) {
    public boolean nil() {
      return balance.signum() == 0;
    }
  }

  /** A held unidentified deposit with its original received date (section 87(4)). */
  public record UnidentifiedItem(
      TakeOnRow row,
      LocalDate receivedDate,
      String description,
      String bankReference,
      BigDecimal amount) {}

  /**
   * A section 86(3) ({@code FIRM_DISCRETION}, no client) or 86(4) ({@code CLIENT_INSTRUCTION})
   * investment. {@code interestRatePercent} as entered; {@code interestRate} the stored fraction
   * ({@code trust_investments.interest_rate numeric(5,4)}, 0..1).
   */
  public record Investment(
      TakeOnRow row,
      UUID investmentAccountId,
      InvestmentBasis basis,
      UUID clientId,
      String clientName,
      UUID matterId,
      String matterName,
      String institution,
      String accountNumber,
      BigDecimal principal,
      BigDecimal interestRatePercent,
      BigDecimal interestRate,
      LocalDate depositDate,
      String instructionReference,
      LocalDate instructionDate,
      boolean endorsementConfirmed,
      BigDecimal accruedInterest,
      BigDecimal fundAmount,
      BigDecimal balancePerConfirmation) {
    public boolean clientInstruction() {
      return basis == InvestmentBasis.CLIENT_INSTRUCTION;
    }
  }

  /** What an item outstanding at cutover is. */
  public enum OutstandingKind {
    DEPOSIT_IN_TRANSIT,
    UNPRESENTED_PAYMENT
  }

  /** An item outstanding at cutover, on its client's matter (§17.3.8). */
  public record OutstandingItem(
      TakeOnRow row,
      OutstandingKind kind,
      LocalDate originalDate,
      BigDecimal amount,
      String reference,
      UUID clientId,
      String clientName,
      UUID matterId,
      String matterName) {}

  /**
   * What the plan adds to one account's books at the cutover date — the EQ-2 terms of {@code
   * TrustBooksService.position} and the outstanding items of {@code TrustLedgerQueryRepository
   * .outstanding}.
   */
  public record Contribution(
      BigDecimal cashbook,
      BigDecimal clientLedgers,
      BigDecimal investmentCreditors,
      BigDecimal fundLedger,
      BigDecimal unidentified,
      BigDecimal interAccount,
      BigDecimal outstandingDeposits,
      BigDecimal outstandingPayments) {}

  /** Every client anchor the commit takes: (G, client) and, for s86(4), (I, client); sorted. */
  public List<ClientAnchor> clientAnchors() {
    var anchors =
        Stream.concat(
            Stream.concat(
                clientBalances.stream()
                    .filter(c -> !c.nil())
                    .map(c -> new ClientAnchor(bankingAccountId, c.clientId())),
                outstandingItems.stream()
                    .map(o -> new ClientAnchor(bankingAccountId, o.clientId()))),
            investments.stream()
                .filter(Investment::clientInstruction)
                .map(i -> new ClientAnchor(i.investmentAccountId(), i.clientId())));
    return anchors.distinct().sorted().toList();
  }

  /** The investment accounts the investment rows name, in UUID order. */
  public List<UUID> investmentAccountIds() {
    return investments.stream().map(Investment::investmentAccountId).distinct().sorted().toList();
  }

  /**
   * Every account the commit locks and marks taken on: the banking account first, then the
   * investment accounts in UUID order (the Phase 92 global order: banking before investment).
   */
  public List<UUID> accountIds() {
    var ids = new ArrayList<UUID>();
    ids.add(bankingAccountId);
    investmentAccountIds().stream().filter(id -> !id.equals(bankingAccountId)).forEach(ids::add);
    return List.copyOf(ids);
  }

  /** The plan's contribution to {@code accountId}'s books at the cutover date. */
  public Contribution contribution(UUID accountId) {
    if (accountId.equals(bankingAccountId)) {
      BigDecimal clients = sum(clientBalances.stream(), ClientBalance::balance);
      BigDecimal unidentified = sum(unidentifiedItems.stream(), UnidentifiedItem::amount);
      BigDecimal placed863 =
          sum(investments.stream().filter(i -> !i.clientInstruction()), Investment::principal);
      return new Contribution(
          clients.add(fundLedger).add(unidentified).subtract(placed863),
          clients,
          BigDecimal.ZERO,
          fundLedger,
          unidentified,
          placed863.negate(),
          outstanding(OutstandingKind.DEPOSIT_IN_TRANSIT),
          outstanding(OutstandingKind.UNPRESENTED_PAYMENT));
    }
    Predicate<Investment> here = i -> i.investmentAccountId().equals(accountId);
    var held = investments.stream().filter(here).toList();
    BigDecimal s864 =
        sum(held.stream().filter(Investment::clientInstruction), Investment::principal);
    BigDecimal s863 = sum(held.stream().filter(i -> !i.clientInstruction()), Investment::principal);
    BigDecimal fund = sum(held.stream(), Investment::fundAmount);
    return new Contribution(
        s864.add(s863).add(fund),
        BigDecimal.ZERO,
        s864,
        fund,
        BigDecimal.ZERO,
        s863,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  private BigDecimal outstanding(OutstandingKind kind) {
    return sum(outstandingItems.stream().filter(o -> o.kind() == kind), OutstandingItem::amount);
  }

  private static <T> BigDecimal sum(Stream<T> items, Function<T, BigDecimal> amount) {
    return items.map(amount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}

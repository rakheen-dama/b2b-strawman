package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The non-client trust creditor ledger (Phase 92, ADR-376, §16.2.4): opens the typed creditor
 * accounts of a trust account and reads their derived balances. It posts nothing — every posting
 * goes through {@code TrustPostingService} (647A+).
 */
@Service
public class TrustCreditorLedgerService {

  private static final String MODULE_ID = "trust_accounting";

  private final TrustCreditorAccountRepository creditorAccountRepository;
  private final TrustAccountRepository trustAccountRepository;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final VerticalModuleGuard moduleGuard;
  private final Clock clock;

  public TrustCreditorLedgerService(
      TrustCreditorAccountRepository creditorAccountRepository,
      TrustAccountRepository trustAccountRepository,
      TrustLedgerQueryRepository ledgerQueries,
      VerticalModuleGuard moduleGuard,
      Clock clock) {
    this.creditorAccountRepository = creditorAccountRepository;
    this.trustAccountRepository = trustAccountRepository;
    this.ledgerQueries = ledgerQueries;
    this.moduleGuard = moduleGuard;
    this.clock = clock;
  }

  /** One non-client creditor account and its derived balance as at {@code asAt} (§16.4.2). */
  public record TrustCreditorAccountResponse(
      UUID id,
      UUID trustAccountId,
      TrustCreditorAccountType accountType,
      String name,
      BigDecimal balance,
      boolean inDebit,
      LocalDate asAt) {}

  /**
   * The trust account's non-client creditor accounts with their balances as at {@code asAt} (today
   * when absent). A closed trust account keeps its ledgers. A Fund ledger in debit is flagged,
   * never refused (founder decision 9, ADR-381).
   */
  @Transactional(readOnly = true)
  public List<TrustCreditorAccountResponse> listCreditorAccounts(
      UUID trustAccountId, LocalDate asAt) {
    moduleGuard.requireModule(MODULE_ID);
    if (!trustAccountRepository.existsById(trustAccountId)) {
      throw new ResourceNotFoundException("TrustAccount", trustAccountId);
    }
    LocalDate date = asAt != null ? asAt : LocalDate.now(clock);
    return creditorAccountRepository.findByTrustAccountId(trustAccountId).stream()
        .sorted(
            Comparator.comparing(TrustCreditorAccount::getAccountType)
                .thenComparing(TrustCreditorAccount::getCreatedAt))
        .map(
            account -> {
              BigDecimal balance = ledgerQueries.creditorAccountBalance(account.getId(), date);
              return new TrustCreditorAccountResponse(
                  account.getId(),
                  account.getTrustAccountId(),
                  account.getAccountType(),
                  account.getName(),
                  balance,
                  balance.signum() < 0,
                  date);
            })
        .toList();
  }

  /**
   * Opens one creditor account of every {@link TrustCreditorAccountType} the trust account lacks,
   * under its default name, and returns the rows created (idempotent: none on a second call).
   *
   * <p>No module guard, by design: an internal helper joined to an already-guarded caller ({@code
   * TrustAccountService.createTrustAccount}) or to the demo seeder, which has no module context
   * (precedent: {@code TrustPostingService.lockClient}). {@code MANDATORY} propagation makes it
   * part of the caller's transaction, so the trust account and its ledgers commit together.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<TrustCreditorAccount> openDefaultAccounts(UUID trustAccountId) {
    var created = new ArrayList<TrustCreditorAccount>();
    for (var type : TrustCreditorAccountType.values()) {
      if (!creditorAccountRepository.existsByTrustAccountIdAndAccountType(trustAccountId, type)) {
        created.add(
            creditorAccountRepository.save(
                new TrustCreditorAccount(trustAccountId, type, type.defaultName())));
      }
    }
    return created;
  }
}

package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.OpeningAuthorities;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon.OpeningAuthority;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Phase 93 / 657A (task 657.4, ADR-386, FD-25): the guards of {@link
 * TrustPostingService#recordOpening} and the take-on refusals of every other posting path — unit
 * level, the repositories mocked. {@code recordOpening} posts a take-on type {@code APPROVED} with
 * the signer as approver, effects from the registry and {@code cleared_at_cutover} from its
 * clearance; it needs the authority's batch and cutover date and runs no {@code
 * DATED_BEFORE_TAKE_ON} guard. {@code record}, {@code recordAwaitingApproval} and {@code
 * recordWithPair} refuse every take-on type; {@code linkBankLine} refuses a cleared row. Each
 * refusal saves nothing.
 */
class TrustPostingServiceRecordOpeningTest {

  private static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");
  private static final Instant SIGNED_AT = Instant.parse("2026-11-16T15:30:00Z");
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final UUID CLIENT = UUID.randomUUID();
  private static final UUID MATTER = UUID.randomUUID();
  private static final UUID MAKER = UUID.randomUUID();
  private static final UUID SIGNER = UUID.randomUUID();
  private static final UUID BATCH = UUID.randomUUID();
  private static final LocalDate CUTOVER = LocalDate.of(2026, 10, 31);

  private TrustTransactionRepository repository;
  private TrustAccountRepository accountRepository;
  private TrustPostingService service;
  private OpeningAuthority authority;

  @BeforeEach
  void setUp() {
    repository = mock(TrustTransactionRepository.class);
    when(repository.save(any(TrustTransaction.class))).thenAnswer(i -> i.getArgument(0));
    accountRepository = mock(TrustAccountRepository.class);
    // The account is already taken on at the cutover date: recordOpening must not care
    when(accountRepository.findTakeOnCutoverDate(ACCOUNT)).thenReturn(Optional.of(CUTOVER));
    service =
        new TrustPostingService(
            repository,
            mock(ClientLedgerCardRepository.class),
            accountRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));
    authority = OpeningAuthorities.of(BATCH, CUTOVER, SIGNER, SIGNED_AT);
  }

  @Test
  void recordOpening_postsApprovedWithTheSigner_effectsAndClearanceFromTheRegistry() {
    var row = service.recordOpening(opening("TAKEON_CLIENT", BATCH, CUTOVER), authority);

    assertThat(row.getStatus()).isEqualTo("APPROVED");
    assertThat(row.getApprovedBy()).isEqualTo(SIGNER);
    assertThat(row.getApprovedAt()).isEqualTo(SIGNED_AT);
    assertThat(row.getPostedAt()).isEqualTo(NOW);
    assertThat(row.getLedger()).isEqualTo(TrustLedger.CLIENT);
    assertThat(row.getCashbookEffect()).isEqualByComparingTo("300.00");
    assertThat(row.getLedgerEffect()).isEqualByComparingTo("300.00");
    assertThat(row.isClearedAtCutover()).isTrue();
    assertThat(row.getTakeOnBatchId()).isEqualTo(BATCH);
    verify(accountRepository).findByIdForShare(ACCOUNT);
    verify(repository).save(row);
  }

  @Test
  void recordOpening_anItemOutstandingAtCutover_isNotCleared() {
    var row = service.recordOpening(opening("TAKEON_PAY_UNPRES", BATCH, CUTOVER), authority);

    assertThat(row.isClearedAtCutover()).isFalse();
    assertThat(row.getCashbookEffect()).isEqualByComparingTo("-300.00");
    assertThat(row.getLedgerEffect()).isEqualByComparingTo("-300.00");
    assertThat(row.getStatus()).isEqualTo("APPROVED");
  }

  @Test
  void recordOpening_refusesARowOfAnotherBatch_anotherDate_orAnOrdinaryType() {
    var otherBatch = opening("TAKEON_CLIENT", UUID.randomUUID(), CUTOVER);
    assertThatThrownBy(() -> service.recordOpening(otherBatch, authority))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("but the authority is for batch " + BATCH);

    var notAtCutover = opening("TAKEON_CLIENT", BATCH, CUTOVER.minusDays(1));
    assertThatThrownBy(() -> service.recordOpening(notAtCutover, authority))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "recordOpening: an opening entry is dated at the cutover date 2026-10-31, not"
                + " 2026-10-30");

    var deposit = opening("DEPOSIT", null, CUTOVER);
    assertThatThrownBy(() -> service.recordOpening(deposit, authority))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("recordOpening is not the posting path for DEPOSIT (approval NONE)");

    assertThatThrownBy(() -> service.recordOpening(opening("TAKEON_CLIENT", BATCH, CUTOVER), null))
        .isInstanceOf(NullPointerException.class);
    verify(repository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = TrustTransactionType.class,
      names = "TAKEON_.*",
      mode = EnumSource.Mode.MATCH_ALL)
  void everyOtherPostingPath_refusesATakeOnType(TrustTransactionType type) {
    assertThatThrownBy(() -> service.record(opening(type.name(), BATCH, CUTOVER.plusDays(1))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "record is not the posting path for " + type + " (a take-on type: recordOpening only)");
    assertThatThrownBy(
            () -> service.recordAwaitingApproval(opening(type.name(), BATCH, CUTOVER.plusDays(1))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "recordAwaitingApproval is not the posting path for "
                + type
                + " (approval AT_SIGN_OFF)");
    verify(repository, never()).save(any());
  }

  @Test
  void record_onATakenOnAccount_refusesAnEntryDatedOnOrBeforeTheCutover() {
    assertThatThrownBy(() -> service.record(opening("DEPOSIT", null, CUTOVER)))
        .isInstanceOfSatisfying(
            ResourceConflictException.class,
            e -> {
              assertThat(e.getBody().getStatus()).isEqualTo(409);
              assertThat(e.getBody().getTitle()).isEqualTo("Entry dated before the take-on");
              assertThat(e.getBody().getDetail())
                  .isEqualTo(
                      "This account was taken on at 31 October 2026. Entries on or before that"
                          + " date belong in the previous system's books.");
              assertThat(e.getBody().getProperties())
                  .containsEntry("code", TrustPostingService.DATED_BEFORE_TAKE_ON);
            });
    verify(repository, never()).save(any());

    assertThat(service.record(opening("DEPOSIT", null, CUTOVER.plusDays(1))).getPostedAt())
        .isEqualTo(NOW);
  }

  @Test
  void linkBankLine_refusesAClearedRow_butAllowsUnlinking() {
    var cleared = service.recordOpening(opening("TAKEON_CLIENT", BATCH, CUTOVER), authority);
    var line = UUID.randomUUID();

    assertThatThrownBy(() -> service.linkBankLine(cleared, line))
        .isInstanceOfSatisfying(
            ResourceConflictException.class,
            e -> {
              assertThat(e.getBody().getStatus()).isEqualTo(409);
              assertThat(e.getBody().getTitle()).isEqualTo("Cleared at the cutover date");
              assertThat(e.getBody().getDetail())
                  .isEqualTo(
                      "This entry is part of the take-on position, which was cleared at the cutover"
                          + " date. It cannot be matched to a bank statement line.");
              assertThat(e.getBody().getProperties())
                  .containsEntry("code", TrustPostingService.CLEARED_AT_CUTOVER);
            });
    assertThat(cleared.getBankStatementLineId()).isNull();

    assertThat(service.linkBankLine(cleared, null).getBankStatementLineId()).isNull();
    var item = service.recordOpening(opening("TAKEON_PAY_UNPRES", BATCH, CUTOVER), authority);
    assertThat(service.linkBankLine(item, line).getBankStatementLineId()).isEqualTo(line);
  }

  private static TrustTransaction opening(String type, UUID batch, LocalDate date) {
    var draft =
        new TrustTransaction(
            ACCOUNT,
            type,
            new BigDecimal("300.00"),
            CLIENT,
            MATTER,
            null,
            "TO-TEST",
            "Brought forward",
            date,
            null,
            MAKER);
    if (batch != null) {
      draft.setTakeOnBatchId(batch);
    }
    return draft;
  }
}

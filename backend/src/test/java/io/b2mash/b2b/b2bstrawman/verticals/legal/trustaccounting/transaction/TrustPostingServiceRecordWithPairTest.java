package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Phase 92 slice 647D (fix cycle 1): the guards of {@link TrustPostingService#recordWithPair} — a
 * WITH_PAIR row ({@code UNIDENT_ALLOC_IN}; 648A's {@code INV_RETURN}) posts only with its pair, and
 * only once that pair is posted with the status its own type posts with (REQUIRED → APPROVED, NONE
 * → RECORDED). Each refusal saves nothing.
 */
class TrustPostingServiceRecordWithPairTest {

  private static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");
  private static final UUID ACCOUNT = UUID.randomUUID();
  private static final UUID CLIENT = UUID.randomUUID();
  private static final UUID MATTER = UUID.randomUUID();
  private static final UUID MAKER = UUID.randomUUID();
  private static final UUID APPROVER = UUID.randomUUID();
  private static final LocalDate DATE = LocalDate.of(2026, 11, 10);

  private TrustTransactionRepository repository;
  private TrustPostingService service;

  @BeforeEach
  void setUp() {
    repository = mock(TrustTransactionRepository.class);
    when(repository.save(any(TrustTransaction.class))).thenAnswer(i -> i.getArgument(0));
    service =
        new TrustPostingService(
            repository,
            mock(ClientLedgerCardRepository.class),
            mock(TrustAccountRepository.class),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void aTypeThatIsNotWithPair_isRefused() {
    var notAPairLeg = draft("DEPOSIT");

    assertThatThrownBy(() -> service.recordWithPair(notAPairLeg, postedPair("APPROVED")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("recordWithPair is not the posting path for DEPOSIT (approval NONE)");
    assertThat(notAPairLeg.getPostedAt()).isNull();
    verify(repository, never()).save(any());
  }

  @Test
  void aPairNotYetPosted_isRefused() {
    var pending = pair("AWAITING_APPROVAL");

    assertThatThrownBy(() -> service.recordWithPair(draft("UNIDENT_ALLOC_IN"), pending))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("recordWithPair: pair " + pending.getId() + " is not posted");
    verify(repository, never()).save(any());
  }

  @Test
  void aPostedPairThatIsNotApproved_isRefused() {
    var reversed = postedPair("REVERSED");

    assertThatThrownBy(() -> service.recordWithPair(draft("UNIDENT_ALLOC_IN"), reversed))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("recordWithPair: pair " + reversed.getId() + " is not posted");
    verify(repository, never()).save(any());
  }

  /** 648A review: the pair's status must be the one its own type posts with. */
  @Test
  void aPairWhoseStatusIsNotItsTypesPostingStatus_isRefused() {
    // A REQUIRED pair that is RECORDED never went through approval
    var recordedRequired = postedPair("RECORDED");
    assertThatThrownBy(() -> service.recordWithPair(draft("UNIDENT_ALLOC_IN"), recordedRequired))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "recordWithPair: pair "
                + recordedRequired.getId()
                + " is RECORDED but its type UNIDENT_ALLOC_OUT posts with approval REQUIRED");

    // A NONE pair (a withdrawal) cannot be APPROVED
    var approvedNone = postedWithdrawal("APPROVED");
    assertThatThrownBy(() -> service.recordWithPair(draft("INV_RETURN"), approvedNone))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "recordWithPair: pair "
                + approvedNone.getId()
                + " is APPROVED but its type INV_WITHDRAW posts with approval NONE");
    verify(repository, never()).save(any());
  }

  @Test
  void aRecordedPairOfANoApprovalType_postsTheLegRecorded_withNoApprovers() {
    var withdrawal = postedWithdrawal("RECORDED");

    var leg = service.recordWithPair(draft("INV_RETURN"), withdrawal);

    assertThat(leg.getStatus()).isEqualTo("RECORDED");
    assertThat(leg.getApprovedBy()).isNull();
    assertThat(leg.getPostedAt()).isEqualTo(NOW);
    verify(repository).save(leg);
  }

  @Test
  void anApprovedPair_postsTheLegApproved_withThePairsApprovers() {
    var pair = postedPair("APPROVED");
    pair.setApprovedBy(APPROVER);
    pair.setApprovedAt(NOW);

    var leg = service.recordWithPair(draft("UNIDENT_ALLOC_IN"), pair);

    assertThat(leg.getStatus()).isEqualTo("APPROVED");
    assertThat(leg.getPostedAt()).isEqualTo(NOW);
    assertThat(leg.getApprovedBy()).isEqualTo(APPROVER);
    assertThat(leg.getLedger()).isEqualTo(TrustLedger.CLIENT);
    assertThat(leg.getLedgerEffect()).isEqualByComparingTo("80.00");
    assertThat(leg.getCashbookEffect()).isEqualByComparingTo("0");
    verify(repository).save(leg);
  }

  private static TrustTransaction draft(String type) {
    return new TrustTransaction(
        ACCOUNT,
        type,
        new BigDecimal("80.00"),
        CLIENT,
        MATTER,
        null,
        "UID-TEST",
        "Deposit received",
        DATE,
        "RECORDED",
        MAKER);
  }

  /** An {@code UNIDENT_ALLOC_OUT} in {@code status}, never posted. */
  private static TrustTransaction pair(String status) {
    var pair =
        new TrustTransaction(
            ACCOUNT,
            "UNIDENT_ALLOC_OUT",
            new BigDecimal("80.00"),
            null,
            null,
            null,
            "UID-TEST",
            "To a client",
            DATE,
            status,
            MAKER);
    ReflectionTestUtils.setField(pair, "id", UUID.randomUUID());
    return pair;
  }

  /** As {@link #pair}, posted (its registry effects applied). */
  private static TrustTransaction postedPair(String status) {
    var pair = pair(status);
    pair.applyPosting(
        TrustTransactionType.UNIDENT_ALLOC_OUT.effectFor(new BigDecimal("80.00")), NOW);
    return pair;
  }

  /** A posted {@code INV_WITHDRAW} (approval NONE — 648A's withdrawal leg) in {@code status}. */
  private static TrustTransaction postedWithdrawal(String status) {
    var withdrawal =
        new TrustTransaction(
            ACCOUNT,
            "INV_WITHDRAW",
            new BigDecimal("80.00"),
            CLIENT,
            MATTER,
            null,
            "INV-WD-TEST",
            "Investment withdrawn",
            DATE,
            status,
            MAKER);
    ReflectionTestUtils.setField(withdrawal, "id", UUID.randomUUID());
    withdrawal.applyPosting(
        TrustTransactionType.INV_WITHDRAW.effectFor(new BigDecimal("80.00")), NOW);
    return withdrawal;
  }
}

package io.b2mash.b2b.b2bstrawman.verticals.legal.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.collections.CollectionsAdvisor.CollectionsAdvice;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

/**
 * Unit tests for {@link TrustAwareCollectionsAdvisor} (592A). Covers the positive/zero paths and —
 * critically — the non-legal-tenant path where the trust tables do not exist, which cannot be
 * exercised in integration tests (every test tenant's schema HAS the trust tables). Mirrors the
 * {@code TrustBoundaryGuardUnitTest} precedent: a {@link DataAccessException} from the ledger query
 * is the "trust tables absent" signal, and the advisor fails OPEN (empty, no error) — the opposite
 * default to the guard's fail-closed posture (ADR-329).
 */
@ExtendWith(MockitoExtension.class)
class TrustAwareCollectionsAdvisorTest {

  @Mock private ClientLedgerCardRepository clientLedgerCardRepository;

  @InjectMocks private TrustAwareCollectionsAdvisor advisor;

  @Test
  void positiveBalance_yieldsTrustFundsAvailableAdviceWithHeldInTrustDetail() {
    when(clientLedgerCardRepository.sumBalancesForCustomer(any()))
        .thenReturn(new BigDecimal("84200.00"));

    List<CollectionsAdvice> advice = advisor.adviseFor(UUID.randomUUID());

    assertThat(advice).hasSize(1);
    assertThat(advice.get(0).signal()).isEqualTo("TRUST_FUNDS_AVAILABLE");
    // Deterministic SA-style format; detail says "held in trust", NOT "available to transfer".
    assertThat(advice.get(0).detail()).isEqualTo("R 84 200,00 held in trust");
  }

  @Test
  void zeroBalance_yieldsNoAdvice() {
    when(clientLedgerCardRepository.sumBalancesForCustomer(any())).thenReturn(BigDecimal.ZERO);

    assertThat(advisor.adviseFor(UUID.randomUUID())).isEmpty();
  }

  @Test
  void negativeBalance_yieldsNoAdvice() {
    when(clientLedgerCardRepository.sumBalancesForCustomer(any()))
        .thenReturn(new BigDecimal("-100.00"));

    assertThat(advisor.adviseFor(UUID.randomUUID())).isEmpty();
  }

  @Test
  void absentTrustTables_failsOpenToEmptyNoError() {
    // Non-legal tenant: the ledger query throws because the relation does not exist.
    when(clientLedgerCardRepository.sumBalancesForCustomer(any()))
        .thenThrow(new DataAccessException("relation \"client_ledger_cards\" does not exist") {});

    assertThat(advisor.adviseFor(UUID.randomUUID())).isEmpty();
  }
}

package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrustBalanceZeroGateTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();

  @Mock private TrustTransactionRepository trustTransactionRepository;
  @Mock private Project project;

  private TrustBalanceZeroGate gate;

  @BeforeEach
  void setUp() {
    gate = new TrustBalanceZeroGate(trustTransactionRepository);
    org.mockito.Mockito.lenient().when(project.getId()).thenReturn(PROJECT_ID);
  }

  @Test
  void passesWhenBalanceIsZero() {
    when(trustTransactionRepository.calculateBalanceByProjectId(PROJECT_ID))
        .thenReturn(BigDecimal.ZERO);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isTrue();
    assertThat(result.code()).isEqualTo("TRUST_BALANCE_ZERO");
  }

  @Test
  void passesWhenRepositoryReturnsNull() {
    when(trustTransactionRepository.calculateBalanceByProjectId(PROJECT_ID)).thenReturn(null);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isTrue();
  }

  @Test
  void failsAndInterpolatesBalanceWhenNonZero() {
    when(trustTransactionRepository.calculateBalanceByProjectId(PROJECT_ID))
        .thenReturn(new BigDecimal("1234.56"));

    var result = gate.evaluate(project);

    assertThat(result.passed()).isFalse();
    assertThat(result.message()).contains("R1234.56");
    assertThat(result.detail()).containsEntry("balance", new BigDecimal("1234.56"));
  }

  @Test
  void orderIsOne() {
    assertThat(gate.order()).isEqualTo(1);
  }
}

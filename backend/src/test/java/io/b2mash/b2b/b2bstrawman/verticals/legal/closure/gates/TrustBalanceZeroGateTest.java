package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrustBalanceZeroGateTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final char NBSP = ' ';

  @Mock private TrustTransactionRepository trustTransactionRepository;
  @Mock private OrgSettingsRepository orgSettingsRepository;
  @Mock private Project project;

  private TrustBalanceZeroGate gate;

  @BeforeEach
  void setUp() {
    gate = new TrustBalanceZeroGate(trustTransactionRepository, orgSettingsRepository);
    lenient().when(project.getId()).thenReturn(PROJECT_ID);
    // No settings row → resolveLocale(null) → en-ZA.
    lenient().when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
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
  void passMessageUsesTheFormatter() {
    when(trustTransactionRepository.calculateBalanceByProjectId(PROJECT_ID))
        .thenReturn(BigDecimal.ZERO);

    var result = gate.evaluate(project);

    assertThat(result.message()).isEqualTo("Matter trust balance is R0,00.");
  }

  @Test
  void failsAndInterpolatesBalanceWhenNonZero() {
    when(trustTransactionRepository.calculateBalanceByProjectId(PROJECT_ID))
        .thenReturn(new BigDecimal("38000.00"));

    var result = gate.evaluate(project);

    assertThat(result.passed()).isFalse();
    // Plain-text JDK en-ZA currency: no space after "R", U+00A0 grouping separator.
    assertThat(result.message())
        .isEqualTo(
            "Matter trust balance is R38"
                + NBSP
                + "000,00. Transfer to client or office before closure.")
        .doesNotContain("38000.00")
        .doesNotContain("&nbsp;")
        .doesNotContain("R%s");
    assertThat(result.detail()).containsEntry("balance", new BigDecimal("38000.00"));
  }

  @Test
  void formatsWithTheTenantCurrencyLocale() {
    OrgSettings settings = mock(OrgSettings.class);
    when(settings.getDefaultCurrency()).thenReturn("USD");
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(settings));
    when(trustTransactionRepository.calculateBalanceByProjectId(PROJECT_ID))
        .thenReturn(new BigDecimal("1234.56"));

    var result = gate.evaluate(project);

    assertThat(result.message()).contains("$1,234.56");
  }

  @Test
  void orderIsOne() {
    assertThat(gate.order()).isEqualTo(1);
  }
}

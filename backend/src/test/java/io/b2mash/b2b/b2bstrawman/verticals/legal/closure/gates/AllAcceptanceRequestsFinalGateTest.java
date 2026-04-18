package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceRequestRepository;
import io.b2mash.b2b.b2bstrawman.acceptance.AcceptanceStatus;
import io.b2mash.b2b.b2bstrawman.project.Project;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllAcceptanceRequestsFinalGateTest {

  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final List<AcceptanceStatus> ACTIVE =
      List.of(AcceptanceStatus.PENDING, AcceptanceStatus.SENT, AcceptanceStatus.VIEWED);

  @Mock private AcceptanceRequestRepository repo;
  @Mock private Project project;

  private AllAcceptanceRequestsFinalGate gate;

  @BeforeEach
  void setUp() {
    gate = new AllAcceptanceRequestsFinalGate(repo);
  }

  @Test
  void passesWhenNoActiveAcceptances() {
    when(project.getCustomerId()).thenReturn(CUSTOMER_ID);
    when(repo.countByCustomerIdAndStatusIn(CUSTOMER_ID, ACTIVE)).thenReturn(0L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isTrue();
    assertThat(result.code()).isEqualTo("ALL_ACCEPTANCE_REQUESTS_FINAL");
  }

  @Test
  void failsAndInterpolatesCountWhenActiveAcceptancesRemain() {
    when(project.getCustomerId()).thenReturn(CUSTOMER_ID);
    when(repo.countByCustomerIdAndStatusIn(CUSTOMER_ID, ACTIVE)).thenReturn(3L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isFalse();
    assertThat(result.message()).contains("3 document acceptances pending");
    assertThat(result.detail()).containsEntry("count", 3L);
  }

  @Test
  void passesWhenCustomerIdIsNull() {
    when(project.getCustomerId()).thenReturn(null);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isTrue();
    verifyNoInteractions(repo);
  }

  @Test
  void orderIsNine() {
    assertThat(gate.order()).isEqualTo(9);
  }
}

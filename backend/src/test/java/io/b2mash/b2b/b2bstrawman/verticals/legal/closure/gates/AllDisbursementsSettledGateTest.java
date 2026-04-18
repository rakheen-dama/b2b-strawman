package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllDisbursementsSettledGateTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();

  @Mock private DisbursementRepository repo;
  @Mock private Project project;

  private AllDisbursementsSettledGate gate;

  @BeforeEach
  void setUp() {
    gate = new AllDisbursementsSettledGate(repo);
    org.mockito.Mockito.lenient().when(project.getId()).thenReturn(PROJECT_ID);
  }

  @Test
  void passesWhenNoApprovedUnbilled() {
    when(repo.countByProjectIdAndApprovalStatusAndBillingStatus(PROJECT_ID, "APPROVED", "UNBILLED"))
        .thenReturn(0L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isTrue();
    assertThat(result.code()).isEqualTo("ALL_DISBURSEMENTS_SETTLED");
  }

  @Test
  void failsAndInterpolatesCountWhenApprovedUnbilledRemain() {
    when(repo.countByProjectIdAndApprovalStatusAndBillingStatus(PROJECT_ID, "APPROVED", "UNBILLED"))
        .thenReturn(2L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isFalse();
    assertThat(result.message()).contains("2 approved disbursements are unbilled");
    assertThat(result.detail()).containsEntry("count", 2L);
  }

  @Test
  void orderIsThree() {
    assertThat(gate.order()).isEqualTo(3);
  }
}

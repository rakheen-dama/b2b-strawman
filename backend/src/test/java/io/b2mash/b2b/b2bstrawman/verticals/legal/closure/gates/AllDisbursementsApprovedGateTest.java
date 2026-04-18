package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.DisbursementRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllDisbursementsApprovedGateTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final List<String> UNAPPROVED = List.of("DRAFT", "PENDING_APPROVAL");

  @Mock private DisbursementRepository repo;
  @Mock private Project project;

  private AllDisbursementsApprovedGate gate;

  @BeforeEach
  void setUp() {
    gate = new AllDisbursementsApprovedGate(repo);
    org.mockito.Mockito.lenient().when(project.getId()).thenReturn(PROJECT_ID);
  }

  @Test
  void passesWhenNoDraftOrPending() {
    when(repo.countByProjectIdAndApprovalStatusIn(PROJECT_ID, UNAPPROVED)).thenReturn(0L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isTrue();
    assertThat(result.code()).isEqualTo("ALL_DISBURSEMENTS_APPROVED");
  }

  @Test
  void failsAndInterpolatesCountWhenAnyUnapproved() {
    when(repo.countByProjectIdAndApprovalStatusIn(PROJECT_ID, UNAPPROVED)).thenReturn(3L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isFalse();
    assertThat(result.message()).contains("3 disbursements are unapproved");
    assertThat(result.detail()).containsEntry("count", 3L);
  }

  @Test
  void orderIsTwo() {
    assertThat(gate.order()).isEqualTo(2);
  }
}

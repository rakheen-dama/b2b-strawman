package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.gates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestStatus;
import io.b2mash.b2b.b2bstrawman.project.Project;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllInfoRequestsClosedGateTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final List<RequestStatus> ACTIVE =
      List.of(RequestStatus.SENT, RequestStatus.IN_PROGRESS);

  @Mock private InformationRequestRepository repo;
  @Mock private Project project;

  private AllInfoRequestsClosedGate gate;

  @BeforeEach
  void setUp() {
    gate = new AllInfoRequestsClosedGate(repo);
    org.mockito.Mockito.lenient().when(project.getId()).thenReturn(PROJECT_ID);
  }

  @Test
  void passesWhenNoActiveRequests() {
    when(repo.countByProjectIdAndStatusIn(PROJECT_ID, ACTIVE)).thenReturn(0L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isTrue();
    assertThat(result.code()).isEqualTo("ALL_INFO_REQUESTS_CLOSED");
  }

  @Test
  void failsAndInterpolatesCountWhenActiveRequestsRemain() {
    when(repo.countByProjectIdAndStatusIn(PROJECT_ID, ACTIVE)).thenReturn(2L);

    var result = gate.evaluate(project);

    assertThat(result.passed()).isFalse();
    assertThat(result.message()).contains("2 client information requests outstanding");
    assertThat(result.detail()).containsEntry("count", 2L);
  }

  @Test
  void orderIsEight() {
    assertThat(gate.order()).isEqualTo(8);
  }
}

package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectStatusTest {

  @Test
  void allowedTransitions_active() {
    assertThat(ProjectStatus.ACTIVE.allowedTransitions())
        .containsExactlyInAnyOrder(ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED);
  }

  @Test
  void allowedTransitions_completed() {
    assertThat(ProjectStatus.COMPLETED.allowedTransitions())
        .containsExactlyInAnyOrder(ProjectStatus.ARCHIVED, ProjectStatus.ACTIVE);
  }

  @Test
  void allowedTransitions_archived() {
    assertThat(ProjectStatus.ARCHIVED.allowedTransitions())
        .containsExactlyInAnyOrder(ProjectStatus.ACTIVE);
  }

  @Test
  void canTransitionTo_valid_returns_true() {
    assertThat(ProjectStatus.ACTIVE.canTransitionTo(ProjectStatus.COMPLETED)).isTrue();
  }

  @Test
  void canTransitionTo_invalid_returns_false() {
    assertThat(ProjectStatus.ARCHIVED.canTransitionTo(ProjectStatus.COMPLETED)).isFalse();
  }

  @Test
  void isTerminal_archived() {
    assertThat(ProjectStatus.ARCHIVED.isTerminal()).isTrue();
  }

  @Test
  void isTerminal_active() {
    assertThat(ProjectStatus.ACTIVE.isTerminal()).isFalse();
  }

  @Test
  void canTransitionTo_self_returns_false() {
    for (ProjectStatus status : ProjectStatus.values()) {
      assertThat(status.canTransitionTo(status))
          .as("Self-transition should be disallowed for %s", status)
          .isFalse();
    }
  }
}

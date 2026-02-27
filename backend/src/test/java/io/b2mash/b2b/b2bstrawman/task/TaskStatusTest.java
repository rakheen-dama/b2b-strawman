package io.b2mash.b2b.b2bstrawman.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskStatusTest {

  @Test
  void allowedTransitions_open() {
    assertThat(TaskStatus.OPEN.allowedTransitions())
        .containsExactlyInAnyOrder(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED);
  }

  @Test
  void allowedTransitions_inProgress() {
    assertThat(TaskStatus.IN_PROGRESS.allowedTransitions())
        .containsExactlyInAnyOrder(TaskStatus.DONE, TaskStatus.OPEN, TaskStatus.CANCELLED);
  }

  @Test
  void allowedTransitions_done() {
    assertThat(TaskStatus.DONE.allowedTransitions()).containsExactlyInAnyOrder(TaskStatus.OPEN);
  }

  @Test
  void allowedTransitions_cancelled() {
    assertThat(TaskStatus.CANCELLED.allowedTransitions())
        .containsExactlyInAnyOrder(TaskStatus.OPEN);
  }

  @Test
  void canTransitionTo_valid_returns_true() {
    assertThat(TaskStatus.OPEN.canTransitionTo(TaskStatus.IN_PROGRESS)).isTrue();
  }

  @Test
  void canTransitionTo_invalid_returns_false() {
    assertThat(TaskStatus.OPEN.canTransitionTo(TaskStatus.DONE)).isFalse();
  }

  @Test
  void isTerminal_done() {
    assertThat(TaskStatus.DONE.isTerminal()).isTrue();
  }

  @Test
  void isTerminal_cancelled() {
    assertThat(TaskStatus.CANCELLED.isTerminal()).isTrue();
  }

  @Test
  void isTerminal_open() {
    assertThat(TaskStatus.OPEN.isTerminal()).isFalse();
  }

  @Test
  void isTerminal_inProgress() {
    assertThat(TaskStatus.IN_PROGRESS.isTerminal()).isFalse();
  }

  @Test
  void canTransitionTo_self_returns_false() {
    for (TaskStatus status : TaskStatus.values()) {
      assertThat(status.canTransitionTo(status))
          .as("Self-transition should be disallowed for %s", status)
          .isFalse();
    }
  }
}

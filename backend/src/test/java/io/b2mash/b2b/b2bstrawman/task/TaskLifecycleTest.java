package io.b2mash.b2b.b2bstrawman.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskLifecycleTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();
  private static final UUID OTHER_MEMBER_ID = UUID.randomUUID();

  private Task buildTask() {
    return new Task(PROJECT_ID, "Test Task", "Description", null, null, null, MEMBER_ID);
  }

  @Test
  void constructor_defaults_to_open_and_medium_priority() {
    var task = buildTask();

    assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
    assertThat(task.getPriority()).isEqualTo(TaskPriority.MEDIUM);
    assertThat(task.getCreatedAt()).isNotNull();
    assertThat(task.getUpdatedAt()).isNotNull();
  }

  @Test
  void claim_transitions_to_in_progress() {
    var task = buildTask();
    task.claim(MEMBER_ID);

    assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(task.getAssigneeId()).isEqualTo(MEMBER_ID);
  }

  @Test
  void claim_from_in_progress_throws() {
    var task = buildTask();
    task.claim(MEMBER_ID);

    assertThatThrownBy(() -> task.claim(OTHER_MEMBER_ID)).isInstanceOf(InvalidStateException.class);
  }

  @Test
  void release_transitions_to_open() {
    var task = buildTask();
    task.claim(MEMBER_ID);
    task.release();

    assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
    assertThat(task.getAssigneeId()).isNull();
  }

  @Test
  void release_from_open_throws() {
    var task = buildTask();

    assertThatThrownBy(() -> task.release()).isInstanceOf(InvalidStateException.class);
  }

  @Test
  void complete_transitions_to_done_and_sets_timestamps() {
    var task = buildTask();
    task.claim(MEMBER_ID);
    task.complete(MEMBER_ID);

    assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    assertThat(task.getCompletedAt()).isNotNull();
    assertThat(task.getCompletedBy()).isEqualTo(MEMBER_ID);
  }

  @Test
  void complete_from_open_throws() {
    var task = buildTask();

    assertThatThrownBy(() -> task.complete(MEMBER_ID)).isInstanceOf(InvalidStateException.class);
  }

  @Test
  void cancel_transitions_to_cancelled() {
    var task = buildTask();
    task.cancel();

    assertThat(task.getStatus()).isEqualTo(TaskStatus.CANCELLED);
    assertThat(task.getCancelledAt()).isNotNull();
  }

  @Test
  void reopen_from_done_clears_timestamps() {
    var task = buildTask();
    task.claim(MEMBER_ID);
    task.complete(MEMBER_ID);

    assertThat(task.getCompletedAt()).isNotNull();
    assertThat(task.getCompletedBy()).isNotNull();

    task.reopen();

    assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
    assertThat(task.getCompletedAt()).isNull();
    assertThat(task.getCompletedBy()).isNull();
    assertThat(task.getCancelledAt()).isNull();
    assertThat(task.getCancelledBy()).isNull();
  }

  @Test
  void reopen_from_cancelled_clears_timestamps() {
    var task = buildTask();
    task.cancel();

    assertThat(task.getCancelledAt()).isNotNull();

    task.reopen();

    assertThat(task.getStatus()).isEqualTo(TaskStatus.OPEN);
    assertThat(task.getCompletedAt()).isNull();
    assertThat(task.getCompletedBy()).isNull();
    assertThat(task.getCancelledAt()).isNull();
    assertThat(task.getCancelledBy()).isNull();
  }
}

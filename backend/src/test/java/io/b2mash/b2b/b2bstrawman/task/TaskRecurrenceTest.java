package io.b2mash.b2b.b2bstrawman.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskRecurrenceTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();

  private Task buildTask() {
    return new Task(PROJECT_ID, "Test Task", "Description", null, null, null, MEMBER_ID);
  }

  @Test
  void newTask_recurrenceFieldsNullByDefault() {
    var task = buildTask();

    assertThat(task.getRecurrenceRule()).isNull();
    assertThat(task.getRecurrenceEndDate()).isNull();
    assertThat(task.getParentTaskId()).isNull();
  }

  @Test
  void isRecurring_returnsTrueWhenRecurrenceRuleSet() {
    var task = buildTask();
    task.setRecurrenceRule("FREQ=MONTHLY;INTERVAL=1");

    assertThat(task.isRecurring()).isTrue();
  }

  @Test
  void isRecurring_returnsFalseForNullRule() {
    var task = buildTask();

    assertThat(task.isRecurring()).isFalse();
  }

  @Test
  void getRootTaskId_returnsOwnIdWhenNoParent() {
    var task = buildTask();

    assertThat(task.getRootTaskId()).isEqualTo(task.getId());
  }

  @Test
  void getRootTaskId_returnsParentIdWhenParentSet() {
    var task = buildTask();
    var parentId = UUID.randomUUID();
    task.setParentTaskId(parentId);

    assertThat(task.getRootTaskId()).isEqualTo(parentId);
  }
}

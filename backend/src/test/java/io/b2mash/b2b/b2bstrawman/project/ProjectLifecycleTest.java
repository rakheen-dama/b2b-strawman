package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectLifecycleTest {

  private static final UUID MEMBER_ID = UUID.randomUUID();

  private Project buildProject() {
    return new Project("Test Project", "Description", MEMBER_ID);
  }

  @Test
  void constructor_defaults_to_active_status() {
    var project = buildProject();

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    assertThat(project.getCreatedAt()).isNotNull();
    assertThat(project.getUpdatedAt()).isNotNull();
  }

  @Test
  void complete_transitions_to_completed_and_sets_timestamps() {
    var project = buildProject();
    project.complete(MEMBER_ID);

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
    assertThat(project.getCompletedAt()).isNotNull();
    assertThat(project.getCompletedBy()).isEqualTo(MEMBER_ID);
  }

  @Test
  void complete_from_archived_throws() {
    var project = buildProject();
    project.archive();

    assertThatThrownBy(() -> project.complete(MEMBER_ID)).isInstanceOf(InvalidStateException.class);
  }

  @Test
  void archive_from_active_transitions_to_archived() {
    var project = buildProject();
    project.archive();

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
    assertThat(project.getArchivedAt()).isNotNull();
  }

  @Test
  void archive_from_completed_transitions_to_archived() {
    var project = buildProject();
    project.complete(MEMBER_ID);
    project.archive();

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
    assertThat(project.getArchivedAt()).isNotNull();
  }

  @Test
  void archive_from_archived_throws() {
    var project = buildProject();
    project.archive();

    assertThatThrownBy(() -> project.archive()).isInstanceOf(InvalidStateException.class);
  }

  @Test
  void reopen_from_completed_clears_timestamps() {
    var project = buildProject();
    project.complete(MEMBER_ID);

    assertThat(project.getCompletedAt()).isNotNull();
    assertThat(project.getCompletedBy()).isNotNull();

    project.reopen();

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    assertThat(project.getCompletedAt()).isNull();
    assertThat(project.getCompletedBy()).isNull();
  }

  @Test
  void reopen_from_archived_clears_timestamps() {
    var project = buildProject();
    project.complete(MEMBER_ID);
    project.archive();

    project.reopen();

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    assertThat(project.getCompletedAt()).isNull();
    assertThat(project.getCompletedBy()).isNull();
    assertThat(project.getArchivedAt()).isNull();
    assertThat(project.getArchivedBy()).isNull();
  }

  @Test
  void isReadOnly_archived() {
    var project = buildProject();
    assertThat(project.isReadOnly()).isFalse();

    project.archive();
    assertThat(project.isReadOnly()).isTrue();
  }
}

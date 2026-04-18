package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the CLOSED lifecycle transitions on {@link Project} (Phase 67, Epic 489A,
 * ADR-248). Operates directly on the entity — no Spring context needed.
 */
class ProjectLifecycleClosureTest {

  private static final UUID MEMBER_ID = UUID.randomUUID();

  @Test
  void activeToClosed_stampsClosedAtAndRetentionClock() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    assertThat(project.getClosedAt()).isNull();
    assertThat(project.getRetentionClockStartedAt()).isNull();

    project.closeMatter(MEMBER_ID);

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.CLOSED);
    assertThat(project.getClosedAt()).isNotNull();
    assertThat(project.getRetentionClockStartedAt())
        .as("retention clock anchors on first close (ADR-249)")
        .isEqualTo(project.getClosedAt());
  }

  @Test
  void completedToClosed_succeeds() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    project.complete(MEMBER_ID);
    assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);

    project.closeMatter(MEMBER_ID);

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.CLOSED);
    assertThat(project.getClosedAt()).isNotNull();
  }

  @Test
  void closedToActive_clearsClosedAt_butPreservesRetentionClock() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    project.closeMatter(MEMBER_ID);
    var originalRetentionAnchor = project.getRetentionClockStartedAt();
    assertThat(originalRetentionAnchor).isNotNull();

    project.reopenMatter();

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    assertThat(project.getClosedAt()).isNull();
    assertThat(project.getRetentionClockStartedAt())
        .as(
            "retention clock anchor is preserved on reopen — soft-cancel lives on the"
                + " RetentionPolicy row (ADR-249)")
        .isEqualTo(originalRetentionAnchor);
  }

  @Test
  void archivedToClosed_throwsInvalidState() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    project.archive(MEMBER_ID);
    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);

    assertThatThrownBy(() -> project.closeMatter(MEMBER_ID))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Cannot close project in status ARCHIVED");
  }

  @Test
  void closedToArchived_throwsInvalidState() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    project.closeMatter(MEMBER_ID);

    assertThatThrownBy(() -> project.archive(MEMBER_ID))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Cannot archive project in status CLOSED");
  }

  @Test
  void reopenFromNonClosedStates_viaReopenMatter_throws() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    // ACTIVE -> ACTIVE via reopenMatter() must fail (no self-transition allowed)
    assertThatThrownBy(project::reopenMatter)
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Cannot reopen project in status ACTIVE");
  }

  @Test
  void closedIsNotTerminal() {
    assertThat(ProjectStatus.CLOSED.isTerminal())
        .as("CLOSED must NOT be terminal — reopen path must stay open")
        .isFalse();
    assertThat(ProjectStatus.CLOSED.canTransitionTo(ProjectStatus.ACTIVE)).isTrue();
  }

  @Test
  void closedProjectIsNotReadOnly() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    project.closeMatter(MEMBER_ID);
    assertThat(project.isReadOnly())
        .as("only ARCHIVED is read-only; CLOSED stays mutable for reopen/audit updates")
        .isFalse();
  }
}

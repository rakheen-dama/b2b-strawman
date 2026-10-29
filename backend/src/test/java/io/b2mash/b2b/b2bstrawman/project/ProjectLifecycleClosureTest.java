package io.b2mash.b2b.b2bstrawman.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the CLOSED lifecycle transitions on {@link Project} (Phase 67, Epic 489A, ADR-248)
 * and the per-matter retention end date stamped at close (Phase 88 U17, ADR-354). Operates directly
 * on the entity — no Spring context needed.
 */
class ProjectLifecycleClosureTest {

  private static final UUID MEMBER_ID = UUID.randomUUID();
  private static final int YEARS = 5;

  @Test
  void activeToClosed_stampsClosedAtClockAndRetentionEndsOn() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    assertThat(project.getClosedAt()).isNull();
    assertThat(project.getRetentionClockStartedAt()).isNull();
    assertThat(project.getRetentionEndsOn()).isNull();

    Instant now = Instant.parse("2026-10-22T09:00:00Z");
    project.closeMatter(now, YEARS);

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.CLOSED);
    assertThat(project.getClosedAt()).isEqualTo(now);
    assertThat(project.getRetentionClockStartedAt())
        .as("retention clock anchors on the close (ADR-249)")
        .isEqualTo(project.getClosedAt());
    assertThat(project.getRetentionEndsOn()).isEqualTo(LocalDate.of(2031, 10, 22));
  }

  @Test
  void completedToClosed_succeeds() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    project.complete(MEMBER_ID);
    assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);

    Instant now = Instant.now();
    project.closeMatter(now, YEARS);

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.CLOSED);
    assertThat(project.getClosedAt()).isEqualTo(now);
    assertThat(project.getRetentionClockStartedAt())
        .as("the clock anchors on the close, not on the earlier completion")
        .isEqualTo(now);
    assertThat(project.getRetentionEndsOn())
        .isEqualTo(now.atZone(ZoneOffset.UTC).toLocalDate().plusYears(YEARS));
  }

  @Test
  void complete_doesNotStartRetentionClock() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);

    project.complete(MEMBER_ID);

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
    assertThat(project.getCompletedAt()).isNotNull();
    assertThat(project.getRetentionClockStartedAt())
        .as("completion no longer starts the retention clock (ADR-354)")
        .isNull();
    assertThat(project.getRetentionEndsOn()).isNull();
  }

  @Test
  void closedToActive_clearsClosedAtAndRetentionEndsOn_keepsClock() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    project.closeMatter(Instant.now(), YEARS);
    var originalRetentionAnchor = project.getRetentionClockStartedAt();
    assertThat(originalRetentionAnchor).isNotNull();
    assertThat(project.getRetentionEndsOn()).isNotNull();

    project.reopenMatter();

    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
    assertThat(project.getClosedAt()).isNull();
    assertThat(project.getRetentionEndsOn())
        .as("an open matter has no scheduled retention end")
        .isNull();
    assertThat(project.getRetentionClockStartedAt())
        .as("the clock is kept on reopen for audit; the next close overwrites it")
        .isEqualTo(originalRetentionAnchor);
  }

  @Test
  void reClose_resetsClockAndEndsOn() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    project.closeMatter(Instant.parse("2026-10-22T09:00:00Z"), YEARS);
    project.reopenMatter();

    Instant second = Instant.parse("2026-10-23T09:00:00Z");
    project.closeMatter(second, YEARS);

    assertThat(project.getClosedAt()).isEqualTo(second);
    assertThat(project.getRetentionClockStartedAt())
        .as("a re-close is a fresh anchor (ADR-249)")
        .isEqualTo(second);
    assertThat(project.getRetentionEndsOn()).isEqualTo(LocalDate.of(2031, 10, 23));
  }

  @Test
  void closeMatter_lateUtcEvening_usesUtcDate() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);

    // 23:30 UTC is already the next day in Johannesburg (UTC+2); the end date follows UTC.
    project.closeMatter(Instant.parse("2026-10-22T23:30:00Z"), YEARS);

    assertThat(project.getRetentionEndsOn()).isEqualTo(LocalDate.of(2031, 10, 22));
  }

  @Test
  void closeMatter_leapDay_clampsToFebruary28() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);

    project.closeMatter(Instant.parse("2024-02-29T08:00:00Z"), YEARS);

    assertThat(project.getRetentionEndsOn()).isEqualTo(LocalDate.of(2029, 2, 28));
  }

  @Test
  void closeMatter_rejectsZeroYears() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);

    assertThatThrownBy(() -> project.closeMatter(Instant.now(), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retentionYears must be at least 1");
    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
  }

  @Test
  void archivedToClosed_throwsInvalidState() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    project.archive(MEMBER_ID);
    assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);

    assertThatThrownBy(() -> project.closeMatter(Instant.now(), YEARS))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Cannot close matter in status ARCHIVED");
  }

  @Test
  void closedToArchived_throwsInvalidState() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    project.closeMatter(Instant.now(), YEARS);

    assertThatThrownBy(() -> project.archive(MEMBER_ID))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Cannot archive matter in status CLOSED");
  }

  @Test
  void reopenFromNonClosedStates_viaReopenMatter_throws() {
    var project = new Project("Smith v Jones", "matter", MEMBER_ID);
    // ACTIVE -> ACTIVE via reopenMatter() must fail (no self-transition allowed)
    assertThatThrownBy(project::reopenMatter)
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Cannot reopen matter in status ACTIVE");
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
    project.closeMatter(Instant.now(), YEARS);
    assertThat(project.isReadOnly())
        .as("only ARCHIVED is read-only; CLOSED stays mutable for reopen/audit updates")
        .isFalse();
  }
}

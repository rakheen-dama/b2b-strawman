package io.b2mash.b2b.b2bstrawman.activity;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.member.Member;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivityMessageFormatterTest {

  private final ActivityMessageFormatter formatter = new ActivityMessageFormatter();

  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID ENTITY_ID = UUID.randomUUID();
  private static final Member ACTOR_MEMBER =
      new Member("clerk_user_1", "alice@test.com", "Alice", null, "member");

  private Map<UUID, Member> actorMap() {
    return Map.of(ACTOR_ID, ACTOR_MEMBER);
  }

  private AuditEvent createEvent(String eventType, String entityType, Map<String, Object> details) {
    var record =
        new AuditEventRecord(
            eventType, entityType, ENTITY_ID, ACTOR_ID, "USER", "API", null, null, details);
    return new AuditEvent(record);
  }

  @Test
  void taskCreatedProducesCorrectMessage() {
    var event = createEvent("task.created", "task", Map.of("title", "Fix login bug"));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).isEqualTo("Alice created task \"Fix login bug\"");
    assertThat(item.actorName()).isEqualTo("Alice");
    assertThat(item.entityType()).isEqualTo("task");
    assertThat(item.entityName()).isEqualTo("Fix login bug");
  }

  @Test
  void taskUpdatedWithAssigneeProducesAssignmentMessage() {
    var event =
        createEvent(
            "task.updated",
            "task",
            Map.of(
                "title", Map.of("from", "Old Title", "to", "Fix login bug"),
                "assignee_id", Map.of("from", "", "to", UUID.randomUUID().toString())));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).isEqualTo("Alice assigned task \"Fix login bug\"");
  }

  @Test
  void taskUpdatedWithStatusProducesStatusChangeMessage() {
    var event =
        createEvent(
            "task.updated",
            "task",
            Map.of(
                "title", Map.of("from", "Fix login bug", "to", "Fix login bug"),
                "status", Map.of("from", "OPEN", "to", "IN_PROGRESS")));
    var item = formatter.format(event, actorMap());
    assertThat(item.message())
        .isEqualTo("Alice changed task \"Fix login bug\" status to IN_PROGRESS");
  }

  @Test
  void taskUpdatedGenericProducesUpdateMessage() {
    var event =
        createEvent("task.updated", "task", Map.of("title", Map.of("from", "Old", "to", "New")));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).isEqualTo("Alice updated task \"New\"");
  }

  @Test
  void taskUpdatedWithBothAssigneeAndStatusPrefersAssignment() {
    var event =
        createEvent(
            "task.updated",
            "task",
            Map.of(
                "title", Map.of("from", "Task", "to", "Task"),
                "assignee_id", Map.of("from", "", "to", UUID.randomUUID().toString()),
                "status", Map.of("from", "OPEN", "to", "IN_PROGRESS")));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).contains("assigned task");
  }

  @Test
  void taskClaimedProducesCorrectMessage() {
    var event =
        createEvent(
            "task.claimed",
            "task",
            Map.of("title", "Fix login bug", "assignee_id", ACTOR_ID.toString()));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).isEqualTo("Alice claimed task \"Fix login bug\"");
  }

  @Test
  void taskReleasedProducesCorrectMessage() {
    var event =
        createEvent(
            "task.released",
            "task",
            Map.of("title", "Fix login bug", "previous_assignee_id", ACTOR_ID.toString()));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).isEqualTo("Alice released task \"Fix login bug\"");
  }

  @Test
  void documentUploadedProducesCorrectMessage() {
    var event = createEvent("document.uploaded", "document", Map.of("file_name", "Q4 Report.pdf"));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).isEqualTo("Alice uploaded document \"Q4 Report.pdf\"");
    assertThat(item.entityName()).isEqualTo("Q4 Report.pdf");
  }

  @Test
  void documentDeletedProducesCorrectMessage() {
    var event = createEvent("document.deleted", "document", Map.of("file_name", "Q4 Report.pdf"));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).isEqualTo("Alice deleted document \"Q4 Report.pdf\"");
  }

  @Test
  void commentCreatedProducesCorrectMessage() {
    var event =
        createEvent(
            "comment.created",
            "comment",
            Map.of(
                "entity_type", "TASK",
                "entity_id", UUID.randomUUID().toString(),
                "body", "Great work!"));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).contains("Alice commented on task");
  }

  @Test
  void timeEntryCreatedFormatsDuration() {
    var event =
        createEvent(
            "time_entry.created",
            "time_entry",
            Map.of("duration_minutes", 150, "task_id", UUID.randomUUID().toString()));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).contains("Alice logged 2h 30m on task");
  }

  @Test
  void timeEntryCreatedFormatsMinutesOnly() {
    var event =
        createEvent(
            "time_entry.created",
            "time_entry",
            Map.of("duration_minutes", 45, "task_id", UUID.randomUUID().toString()));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).contains("logged 45m");
  }

  @Test
  void timeEntryCreatedFormatsHoursOnly() {
    var event =
        createEvent(
            "time_entry.created",
            "time_entry",
            Map.of("duration_minutes", 120, "task_id", UUID.randomUUID().toString()));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).contains("logged 2h");
  }

  @Test
  void projectMemberAddedProducesCorrectMessage() {
    var event = createEvent("project_member.added", "project_member", Map.of("name", "Bob"));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).isEqualTo("Alice added Bob to the project");
    assertThat(item.entityName()).isEqualTo("Bob");
  }

  @Test
  void projectMemberRemovedProducesCorrectMessage() {
    var event = createEvent("project_member.removed", "project_member", Map.of("name", "Bob"));
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).isEqualTo("Alice removed Bob from the project");
  }

  @Test
  void unknownEventTypeProducesFallbackMessage() {
    var event = createEvent("customer.updated", "customer", Map.of());
    var item = formatter.format(event, actorMap());
    assertThat(item.message()).isEqualTo("Alice performed customer.updated on customer");
  }

  @Test
  void nullActorIdProducesSystemName() {
    var record =
        new AuditEventRecord(
            "task.created",
            "task",
            ENTITY_ID,
            null,
            "SYSTEM",
            "INTERNAL",
            null,
            null,
            Map.of("title", "Auto-created task"));
    var event = new AuditEvent(record);
    var item = formatter.format(event, Map.of());
    assertThat(item.actorName()).isEqualTo("System");
    assertThat(item.actorAvatarUrl()).isNull();
    assertThat(item.message()).contains("System created task");
  }

  @Test
  void unknownActorIdFallsBackToUuidString() {
    UUID unknownId = UUID.randomUUID();
    var record =
        new AuditEventRecord(
            "task.created",
            "task",
            ENTITY_ID,
            unknownId,
            "USER",
            "API",
            null,
            null,
            Map.of("title", "Some task"));
    var event = new AuditEvent(record);
    var item = formatter.format(event, Map.of());
    assertThat(item.actorName()).isEqualTo(unknownId.toString());
  }
}

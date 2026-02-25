package io.b2mash.b2b.b2bstrawman.activity;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.member.Member;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps audit events to human-readable activity feed messages. */
@Component
public class ActivityMessageFormatter {

  public ActivityItem format(AuditEvent event, Map<UUID, Member> actorMap) {
    String actorName = resolveActorName(event.getActorId(), actorMap, event.getDetails());
    String actorAvatarUrl = resolveActorAvatarUrl(event.getActorId(), actorMap);
    Map<String, Object> details = event.getDetails() != null ? event.getDetails() : Map.of();

    String message = formatMessage(event.getEventType(), event.getEntityType(), actorName, details);
    String entityName = resolveEntityName(event.getEntityType(), details);

    return new ActivityItem(
        event.getId(),
        message,
        actorName,
        actorAvatarUrl,
        event.getEntityType(),
        event.getEntityId(),
        entityName,
        event.getEventType(),
        event.getOccurredAt());
  }

  private String formatMessage(
      String eventType, String entityType, String actorName, Map<String, Object> details) {
    return switch (eventType) {
      case "task.created" -> "%s created task \"%s\"".formatted(actorName, getTitle(details));
      case "task.updated" -> formatTaskUpdated(actorName, details);
      case "task.claimed" -> "%s claimed task \"%s\"".formatted(actorName, getTitle(details));
      case "task.released" -> "%s released task \"%s\"".formatted(actorName, getTitle(details));
      case "document.uploaded" ->
          "%s uploaded document \"%s\"".formatted(actorName, getFileName(details));
      case "document.updated" ->
          "%s updated document \"%s\"".formatted(actorName, getFileName(details));
      case "document.deleted" ->
          "%s deleted document \"%s\"".formatted(actorName, getFileName(details));
      case "comment.created" ->
          "%s commented on %s \"%s\""
              .formatted(actorName, getParentType(details), getParentName(details));
      case "comment.updated" ->
          "%s edited a comment on %s \"%s\""
              .formatted(actorName, getParentType(details), getParentName(details));
      case "comment.deleted" ->
          "%s deleted a comment on %s \"%s\""
              .formatted(actorName, getParentType(details), getParentName(details));
      case "comment.visibility_changed" ->
          "%s changed comment visibility to %s"
              .formatted(actorName, details.getOrDefault("new_visibility", "unknown"));
      case "time_entry.created" ->
          "%s logged %s on task \"%s\""
              .formatted(actorName, formatDuration(details), getTaskTitle(details));
      case "time_entry.updated" ->
          "%s updated time entry on task \"%s\"".formatted(actorName, getTaskTitle(details));
      case "time_entry.deleted" ->
          "%s deleted time entry on task \"%s\"".formatted(actorName, getTaskTitle(details));
      case "project_member.added" ->
          "%s added %s to the project"
              .formatted(actorName, details.getOrDefault("name", "a member"));
      case "project_member.removed" ->
          "%s removed %s from the project"
              .formatted(actorName, details.getOrDefault("name", "a member"));
      default -> "%s performed %s on %s".formatted(actorName, eventType, entityType);
    };
  }

  private String formatTaskUpdated(String actorName, Map<String, Object> details) {
    String title = getTitle(details);
    // Check for assignment change (higher priority)
    if (details.containsKey("assignee_id")) {
      return "%s assigned task \"%s\"".formatted(actorName, title);
    }
    // Check for status change
    if (details.containsKey("status")) {
      Object statusVal = details.get("status");
      String newStatus = extractNewValue(statusVal);
      return "%s changed task \"%s\" status to %s".formatted(actorName, title, newStatus);
    }
    return "%s updated task \"%s\"".formatted(actorName, title);
  }

  private String getTitle(Map<String, Object> details) {
    Object title = details.get("title");
    if (title instanceof String s) {
      return s;
    }
    if (title instanceof Map<?, ?> m) {
      Object toVal = m.get("to");
      if (toVal instanceof String s && !s.isEmpty()) {
        return s;
      }
      Object fromVal = m.get("from");
      if (fromVal instanceof String s) {
        return s;
      }
    }
    return "unknown";
  }

  private String getFileName(Map<String, Object> details) {
    Object fileName = details.get("file_name");
    return fileName instanceof String s ? s : "unknown";
  }

  private String getParentType(Map<String, Object> details) {
    Object entityType = details.get("entity_type");
    if (entityType instanceof String s) {
      return s.toLowerCase();
    }
    return "entity";
  }

  private String getParentName(Map<String, Object> details) {
    // Comment details store entity_type and entity_id but not entity name
    return getParentType(details);
  }

  private String getTaskTitle(Map<String, Object> details) {
    // Time entries reference tasks; title may not be in details
    Object title = details.get("title");
    if (title instanceof String s) {
      return s;
    }
    return "a task";
  }

  private String formatDuration(Map<String, Object> details) {
    Object durationObj = details.get("duration_minutes");
    if (durationObj instanceof Number n) {
      int minutes = n.intValue();
      int hours = minutes / 60;
      int remainingMinutes = minutes % 60;
      if (hours > 0 && remainingMinutes > 0) {
        return "%dh %dm".formatted(hours, remainingMinutes);
      } else if (hours > 0) {
        return "%dh".formatted(hours);
      } else {
        return "%dm".formatted(remainingMinutes);
      }
    }
    return "some time";
  }

  private String extractNewValue(Object val) {
    if (val instanceof String s) {
      return s;
    }
    if (val instanceof Map<?, ?> m) {
      Object toVal = m.get("to");
      if (toVal instanceof String s) {
        return s;
      }
    }
    return "unknown";
  }

  private String resolveActorName(
      UUID actorId, Map<UUID, Member> actorMap, Map<String, Object> details) {
    if (actorId == null) {
      return "System";
    }
    Member member = actorMap.get(actorId);
    if (member != null) {
      return member.getName();
    }
    // Fallback for non-member actors (e.g. portal customers)
    if (details != null && details.get("actor_name") instanceof String name) {
      return name;
    }
    return "Unknown";
  }

  private String resolveActorAvatarUrl(UUID actorId, Map<UUID, Member> actorMap) {
    if (actorId == null) {
      return null;
    }
    Member member = actorMap.get(actorId);
    return member != null ? member.getAvatarUrl() : null;
  }

  private String resolveEntityName(String entityType, Map<String, Object> details) {
    return switch (entityType) {
      case "task" -> getTitle(details);
      case "document" -> getFileName(details);
      case "comment" -> getParentName(details);
      case "time_entry" -> getTaskTitle(details);
      case "project_member" -> {
        Object name = details.get("name");
        yield name instanceof String s ? s : "member";
      }
      default -> "unknown";
    };
  }
}

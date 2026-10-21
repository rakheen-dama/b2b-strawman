package io.b2mash.b2b.b2bstrawman.diary;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.task.Task;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtDate;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTracker;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/**
 * One Diary row (architecture §12.2.6). Null fields are omitted from the JSON. {@code href} is
 * relative to {@code /org/{slug}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiaryEventResponse(
    UUID id,
    DiaryKind kind,
    LocalDate date,
    LocalTime time,
    String title,
    String subtype,
    String status,
    UUID matterId,
    String matterName,
    String matterReference,
    UUID clientId,
    String clientName,
    UUID feeEarnerId,
    String feeEarnerName,
    boolean overdue,
    String href) {

  /** Terminal statuses across the four sources (§12.2.6 + prescription terminals). */
  private static final Set<String> TERMINAL =
      Set.of("DONE", "CANCELLED", "HEARD", "INTERRUPTED", "EXPIRED");

  /** The matter an event belongs to, with its client resolved. */
  public record MatterRef(
      UUID id, String name, String reference, UUID clientId, String clientName) {}

  /** The resolved fee earner (task assignee, else matter lead); {@link #NONE} when neither. */
  public record FeeEarnerRef(UUID id, String name) {
    public static final FeeEarnerRef NONE = new FeeEarnerRef(null, null);
  }

  static DiaryEventResponse fromTask(Task t, MatterRef m, FeeEarnerRef fe, LocalDate today) {
    String status = t.getStatus().name();
    return new DiaryEventResponse(
        t.getId(),
        DiaryKind.TASK_DUE,
        t.getDueDate(),
        null,
        t.getTitle(),
        null,
        status,
        m.id(),
        m.name(),
        m.reference(),
        m.clientId(),
        m.clientName(),
        fe.id(),
        fe.name(),
        isOverdue(t.getDueDate(), status, today),
        "/matters/" + m.id() + "?taskId=" + t.getId());
  }

  static DiaryEventResponse fromMatterDue(
      Project p, MatterRef m, FeeEarnerRef lead, LocalDate today) {
    String status = p.getStatus().name();
    return new DiaryEventResponse(
        p.getId(),
        DiaryKind.MATTER_DUE,
        p.getDueDate(),
        null,
        p.getName(),
        null,
        status,
        m.id(),
        m.name(),
        m.reference(),
        m.clientId(),
        m.clientName(),
        lead.id(),
        lead.name(),
        isOverdue(p.getDueDate(), status, today),
        "/matters/" + p.getId());
  }

  static DiaryEventResponse fromCourtDate(
      CourtDate cd, MatterRef m, FeeEarnerRef lead, LocalDate today) {
    String title =
        cd.getDescription() == null || cd.getDescription().isBlank()
            ? cd.getCourtName()
            : cd.getCourtName() + " — " + cd.getDescription();
    return new DiaryEventResponse(
        cd.getId(),
        DiaryKind.COURT_DATE,
        cd.getScheduledDate(),
        cd.getScheduledTime(),
        title,
        cd.getDateType(),
        cd.getStatus(),
        m.id(),
        m.name(),
        m.reference(),
        m.clientId(),
        m.clientName(),
        lead.id(),
        lead.name(),
        isOverdue(cd.getScheduledDate(), cd.getStatus(), today),
        "/matters/" + m.id() + "?tab=court-dates&courtDateId=" + cd.getId());
  }

  static DiaryEventResponse fromPrescription(
      PrescriptionTracker pt, MatterRef m, FeeEarnerRef lead, LocalDate today) {
    return new DiaryEventResponse(
        pt.getId(),
        DiaryKind.PRESCRIPTION,
        pt.getPrescriptionDate(),
        null,
        "Prescription — " + prescriptionLabel(pt),
        pt.getPrescriptionType(),
        pt.getStatus(),
        m.id(),
        m.name(),
        m.reference(),
        m.clientId(),
        m.clientName(),
        lead.id(),
        lead.name(),
        isOverdue(pt.getPrescriptionDate(), pt.getStatus(), today),
        "/matters/" + m.id() + "?tab=court-dates&prescriptionId=" + pt.getId());
  }

  /** §12.2.6: dated before today and not in a terminal status. */
  static boolean isOverdue(LocalDate date, String status, LocalDate today) {
    return date.isBefore(today) && !TERMINAL.contains(status);
  }

  /** Same wording as the frontend create-prescription dialog. */
  private static String prescriptionLabel(PrescriptionTracker pt) {
    String type = pt.getPrescriptionType();
    if (type == null) {
      return "Custom";
    }
    return switch (type) {
      case "GENERAL_3Y" -> "General (3 years)";
      case "DEBT_6Y" -> "Debt (6 years)";
      case "MORTGAGE_30Y" -> "Mortgage (30 years)";
      case "DELICT_3Y" -> "Delict (3 years)";
      case "CONTRACT_3Y" -> "Contract (3 years)";
      case "CUSTOM" ->
          pt.getCustomYears() != null ? "Custom (" + pt.getCustomYears() + " years)" : "Custom";
      default -> type;
    };
  }
}

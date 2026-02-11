package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TimeEntryController {

  private final TimeEntryService timeEntryService;
  private final MemberRepository memberRepository;

  public TimeEntryController(TimeEntryService timeEntryService, MemberRepository memberRepository) {
    this.timeEntryService = timeEntryService;
    this.memberRepository = memberRepository;
  }

  @PostMapping("/api/tasks/{taskId}/time-entries")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TimeEntryResponse> createTimeEntry(
      @PathVariable UUID taskId, @Valid @RequestBody CreateTimeEntryRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var entry =
        timeEntryService.createTimeEntry(
            taskId,
            request.date(),
            request.durationMinutes(),
            request.billable() != null ? request.billable() : true,
            request.rateCents(),
            request.description(),
            memberId,
            orgRole);

    var names = resolveNames(List.of(entry));
    return ResponseEntity.created(URI.create("/api/time-entries/" + entry.getId()))
        .body(TimeEntryResponse.from(entry, names));
  }

  @GetMapping("/api/tasks/{taskId}/time-entries")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TimeEntryResponse>> listTimeEntries(@PathVariable UUID taskId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var entries = timeEntryService.listTimeEntriesByTask(taskId, memberId, orgRole);
    var names = resolveNames(entries);
    var response = entries.stream().map(e -> TimeEntryResponse.from(e, names)).toList();
    return ResponseEntity.ok(response);
  }

  @PutMapping("/api/time-entries/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TimeEntryResponse> updateTimeEntry(
      @PathVariable UUID id, @Valid @RequestBody UpdateTimeEntryRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var entry =
        timeEntryService.updateTimeEntry(
            id,
            request.date(),
            request.durationMinutes(),
            request.billable(),
            request.rateCents(),
            request.description(),
            memberId,
            orgRole);

    var names = resolveNames(List.of(entry));
    return ResponseEntity.ok(TimeEntryResponse.from(entry, names));
  }

  @DeleteMapping("/api/time-entries/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteTimeEntry(@PathVariable UUID id) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    timeEntryService.deleteTimeEntry(id, memberId, orgRole);
    return ResponseEntity.noContent().build();
  }

  /**
   * Batch-loads member names for all member IDs referenced by the given time entries. Returns a map
   * of member UUID to display name.
   */
  private Map<UUID, String> resolveNames(List<TimeEntry> entries) {
    var ids =
        entries.stream().map(TimeEntry::getMemberId).filter(Objects::nonNull).distinct().toList();

    if (ids.isEmpty()) {
      return Map.of();
    }

    return memberRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Member::getId, Member::getName, (a, b) -> a));
  }

  // --- DTOs ---

  public record CreateTimeEntryRequest(
      @NotNull(message = "date is required") LocalDate date,
      @NotNull(message = "durationMinutes is required")
          @Positive(message = "durationMinutes must be positive")
          Integer durationMinutes,
      Boolean billable,
      Integer rateCents,
      String description) {}

  public record UpdateTimeEntryRequest(
      LocalDate date,
      @Positive(message = "durationMinutes must be positive") Integer durationMinutes,
      Boolean billable,
      Integer rateCents,
      String description) {}

  public record TimeEntryResponse(
      UUID id,
      UUID taskId,
      UUID memberId,
      String memberName,
      LocalDate date,
      int durationMinutes,
      boolean billable,
      Integer rateCents,
      String description,
      Instant createdAt,
      Instant updatedAt) {

    public static TimeEntryResponse from(TimeEntry entry, Map<UUID, String> memberNames) {
      return new TimeEntryResponse(
          entry.getId(),
          entry.getTaskId(),
          entry.getMemberId(),
          memberNames.get(entry.getMemberId()),
          entry.getDate(),
          entry.getDurationMinutes(),
          entry.isBillable(),
          entry.getRateCents(),
          entry.getDescription(),
          entry.getCreatedAt(),
          entry.getUpdatedAt());
    }
  }
}

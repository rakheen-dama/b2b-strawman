package io.b2mash.b2b.b2bstrawman.schedule;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.schedule.dto.CreateScheduleRequest;
import io.b2mash.b2b.b2bstrawman.schedule.dto.ScheduleExecutionResponse;
import io.b2mash.b2b.b2bstrawman.schedule.dto.ScheduleResponse;
import io.b2mash.b2b.b2bstrawman.schedule.dto.UpdateScheduleRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedules")
public class RecurringScheduleController {

  private final RecurringScheduleService scheduleService;

  public RecurringScheduleController(RecurringScheduleService scheduleService) {
    this.scheduleService = scheduleService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<ScheduleResponse>> listSchedules(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) UUID templateId) {
    return ResponseEntity.ok(scheduleService.list(status, customerId, templateId));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ScheduleResponse> getSchedule(@PathVariable UUID id) {
    return ResponseEntity.ok(scheduleService.get(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ScheduleResponse> createSchedule(
      @Valid @RequestBody CreateScheduleRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    var response = scheduleService.create(request, memberId);
    return ResponseEntity.created(URI.create("/api/schedules/" + response.id())).body(response);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ScheduleResponse> updateSchedule(
      @PathVariable UUID id, @Valid @RequestBody UpdateScheduleRequest request) {
    return ResponseEntity.ok(scheduleService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> deleteSchedule(@PathVariable UUID id) {
    scheduleService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/pause")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ScheduleResponse> pauseSchedule(@PathVariable UUID id) {
    return ResponseEntity.ok(scheduleService.pause(id));
  }

  @PostMapping("/{id}/resume")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ScheduleResponse> resumeSchedule(@PathVariable UUID id) {
    return ResponseEntity.ok(scheduleService.resume(id));
  }

  @GetMapping("/{id}/executions")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<ScheduleExecutionResponse>> listExecutions(@PathVariable UUID id) {
    return ResponseEntity.ok(scheduleService.listExecutions(id));
  }
}

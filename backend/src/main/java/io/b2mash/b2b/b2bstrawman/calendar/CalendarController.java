package io.b2mash.b2b.b2bstrawman.calendar;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

  private final CalendarService calendarService;

  public CalendarController(CalendarService calendarService) {
    this.calendarService = calendarService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CalendarResponse> getCalendarItems(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID projectId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) UUID assigneeId,
      @RequestParam(required = false, defaultValue = "false") boolean overdue) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var response =
        calendarService.getCalendarItems(
            memberId, orgRole, from, to, projectId, type, assigneeId, overdue);
    return ResponseEntity.ok(response);
  }

  // --- DTOs ---

  public record CalendarItemDto(
      UUID id,
      String name,
      String itemType,
      LocalDate dueDate,
      String status,
      String priority,
      UUID assigneeId,
      UUID projectId,
      String projectName) {}

  public record CalendarResponse(List<CalendarItemDto> items, int overdueCount) {}
}

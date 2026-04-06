package io.b2mash.b2b.b2bstrawman.calendar;

import io.b2mash.b2b.b2bstrawman.calendar.CalendarService.CalendarResponse;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
  public ResponseEntity<CalendarResponse> getCalendarItems(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID projectId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) UUID assigneeId,
      @RequestParam(required = false, defaultValue = "false") boolean overdue,
      ActorContext actor) {
    var response =
        calendarService.getCalendarItems(actor, from, to, projectId, type, assigneeId, overdue);
    return ResponseEntity.ok(response);
  }
}

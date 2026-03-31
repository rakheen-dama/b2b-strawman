package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.CancelRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.CourtDateFilters;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.CourtDateResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.CreateCourtDateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.OutcomeRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.PostponeRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.UpcomingResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.UpdateCourtDateRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/court-dates")
public class CourtCalendarController {

  private final CourtCalendarService courtCalendarService;

  public CourtCalendarController(CourtCalendarService courtCalendarService) {
    this.courtCalendarService = courtCalendarService;
  }

  @GetMapping
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<Page<CourtDateResponse>> list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateTo,
      @RequestParam(required = false) String dateType,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) UUID projectId,
      Pageable pageable) {
    return ResponseEntity.ok(
        courtCalendarService.list(
            new CourtDateFilters(dateFrom, dateTo, dateType, status, customerId, projectId),
            pageable));
  }

  @GetMapping("/{id}")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<CourtDateResponse> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(courtCalendarService.getById(id));
  }

  @PostMapping
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<CourtDateResponse> create(
      @Valid @RequestBody CreateCourtDateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(courtCalendarService.createCourtDate(request, RequestScopes.requireMemberId()));
  }

  @PutMapping("/{id}")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<CourtDateResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateCourtDateRequest request) {
    return ResponseEntity.ok(courtCalendarService.updateCourtDate(id, request));
  }

  @PostMapping("/{id}/postpone")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<CourtDateResponse> postpone(
      @PathVariable UUID id, @Valid @RequestBody PostponeRequest request) {
    return ResponseEntity.ok(courtCalendarService.postponeCourtDate(id, request));
  }

  @PostMapping("/{id}/cancel")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<CourtDateResponse> cancel(
      @PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
    return ResponseEntity.ok(courtCalendarService.cancelCourtDate(id, request));
  }

  @PostMapping("/{id}/outcome")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<CourtDateResponse> recordOutcome(
      @PathVariable UUID id, @Valid @RequestBody OutcomeRequest request) {
    return ResponseEntity.ok(courtCalendarService.recordOutcome(id, request));
  }

  @GetMapping("/upcoming")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<UpcomingResponse> upcoming() {
    return ResponseEntity.ok(courtCalendarService.getUpcoming());
  }
}

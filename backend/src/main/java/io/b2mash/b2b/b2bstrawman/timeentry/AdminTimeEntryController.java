package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/time-entries")
public class AdminTimeEntryController {

  private final TimeEntryService timeEntryService;

  public AdminTimeEntryController(TimeEntryService timeEntryService) {
    this.timeEntryService = timeEntryService;
  }

  @PostMapping("/re-snapshot")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<ReSnapshotResponse> reSnapshot(
      @Valid @RequestBody ReSnapshotRequest request) {
    if (request.matterId() == null
        && request.memberId() == null
        && request.fromDate() == null
        && request.toDate() == null) {
      throw new InvalidStateException(
          "At least one filter required",
          "Provide at least one of matterId, memberId, fromDate, or toDate");
    }

    var result =
        timeEntryService.reSnapshotRates(
            request.matterId(), request.memberId(), request.fromDate(), request.toDate());

    return ResponseEntity.ok(
        new ReSnapshotResponse(
            result.entriesProcessed(), result.entriesUpdated(), result.entriesSkipped()));
  }

  // --- DTOs ---

  public record ReSnapshotRequest(
      UUID matterId, UUID memberId, LocalDate fromDate, LocalDate toDate) {}

  public record ReSnapshotResponse(int entriesProcessed, int entriesUpdated, int entriesSkipped) {}
}

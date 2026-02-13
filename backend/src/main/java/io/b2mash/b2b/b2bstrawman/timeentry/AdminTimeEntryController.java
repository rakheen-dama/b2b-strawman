package io.b2mash.b2b.b2bstrawman.timeentry;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ReSnapshotResponse> reSnapshot(
      @Valid @RequestBody ReSnapshotRequest request) {
    if (request.projectId() == null
        && request.memberId() == null
        && request.fromDate() == null
        && request.toDate() == null) {
      throw new InvalidStateException(
          "At least one filter required",
          "Provide at least one of projectId, memberId, fromDate, or toDate");
    }

    var result =
        timeEntryService.reSnapshotRates(
            request.projectId(), request.memberId(), request.fromDate(), request.toDate());

    return ResponseEntity.ok(
        new ReSnapshotResponse(
            result.entriesProcessed(), result.entriesUpdated(), result.entriesSkipped()));
  }

  // --- DTOs ---

  public record ReSnapshotRequest(
      UUID projectId, UUID memberId, LocalDate fromDate, LocalDate toDate) {}

  public record ReSnapshotResponse(int entriesProcessed, int entriesUpdated, int entriesSkipped) {}
}

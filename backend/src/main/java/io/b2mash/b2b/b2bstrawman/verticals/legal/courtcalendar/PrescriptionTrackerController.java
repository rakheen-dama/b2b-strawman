package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.CreatePrescriptionTrackerRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.InterruptRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.PrescriptionTrackerFilters;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.PrescriptionTrackerResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.PrescriptionTrackerService.UpdatePrescriptionTrackerRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/prescription-trackers")
public class PrescriptionTrackerController {

  private final PrescriptionTrackerService prescriptionTrackerService;

  public PrescriptionTrackerController(PrescriptionTrackerService prescriptionTrackerService) {
    this.prescriptionTrackerService = prescriptionTrackerService;
  }

  @GetMapping
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<Page<PrescriptionTrackerResponse>> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) UUID projectId,
      Pageable pageable) {
    return ResponseEntity.ok(
        prescriptionTrackerService.list(
            new PrescriptionTrackerFilters(status, customerId, projectId), pageable));
  }

  @GetMapping("/{id}")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<PrescriptionTrackerResponse> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(prescriptionTrackerService.getById(id));
  }

  @PostMapping
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<PrescriptionTrackerResponse> create(
      @Valid @RequestBody CreatePrescriptionTrackerRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(prescriptionTrackerService.create(request, RequestScopes.requireMemberId()));
  }

  @PutMapping("/{id}")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<PrescriptionTrackerResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdatePrescriptionTrackerRequest request) {
    return ResponseEntity.ok(prescriptionTrackerService.update(id, request));
  }

  @PostMapping("/{id}/interrupt")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<PrescriptionTrackerResponse> interrupt(
      @PathVariable UUID id, @Valid @RequestBody InterruptRequest request) {
    return ResponseEntity.ok(prescriptionTrackerService.interrupt(id, request));
  }
}

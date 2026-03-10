package io.b2mash.b2b.b2bstrawman.informationrequest;

import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.AddItemRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.CreateInformationRequestRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.DashboardSummaryResponse;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.InformationRequestResponse;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.RejectItemRequest;
import io.b2mash.b2b.b2bstrawman.informationrequest.dto.InformationRequestDtos.UpdateInformationRequestRequest;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InformationRequestController {

  private final InformationRequestService informationRequestService;

  public InformationRequestController(InformationRequestService informationRequestService) {
    this.informationRequestService = informationRequestService;
  }

  @PostMapping("/api/information-requests")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<InformationRequestResponse> create(
      @Valid @RequestBody CreateInformationRequestRequest request) {
    var response = informationRequestService.create(request);
    return ResponseEntity.created(URI.create("/api/information-requests/" + response.id()))
        .body(response);
  }

  @GetMapping("/api/information-requests")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<InformationRequestResponse>> list(
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) UUID projectId,
      @RequestParam(required = false) RequestStatus status) {
    return ResponseEntity.ok(informationRequestService.list(customerId, projectId, status));
  }

  @GetMapping("/api/information-requests/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<InformationRequestResponse> get(@PathVariable UUID id) {
    return ResponseEntity.ok(informationRequestService.getById(id));
  }

  @PutMapping("/api/information-requests/{id}")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<InformationRequestResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateInformationRequestRequest request) {
    return ResponseEntity.ok(informationRequestService.updateRequest(id, request));
  }

  @PostMapping("/api/information-requests/{id}/send")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<InformationRequestResponse> send(@PathVariable UUID id) {
    return ResponseEntity.ok(informationRequestService.send(id));
  }

  @PostMapping("/api/information-requests/{id}/cancel")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<InformationRequestResponse> cancel(@PathVariable UUID id) {
    return ResponseEntity.ok(informationRequestService.cancel(id));
  }

  @PostMapping("/api/information-requests/{id}/items")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<InformationRequestResponse> addItem(
      @PathVariable UUID id, @Valid @RequestBody AddItemRequest request) {
    return ResponseEntity.ok(informationRequestService.addItem(id, request));
  }

  @PostMapping("/api/information-requests/{id}/items/{itemId}/accept")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<InformationRequestResponse> acceptItem(
      @PathVariable UUID id, @PathVariable UUID itemId) {
    return ResponseEntity.ok(informationRequestService.acceptItem(id, itemId));
  }

  @PostMapping("/api/information-requests/{id}/items/{itemId}/reject")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<InformationRequestResponse> rejectItem(
      @PathVariable UUID id,
      @PathVariable UUID itemId,
      @Valid @RequestBody RejectItemRequest request) {
    return ResponseEntity.ok(informationRequestService.rejectItem(id, itemId, request.reason()));
  }

  @PostMapping("/api/information-requests/{id}/resend-notification")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<InformationRequestResponse> resendNotification(@PathVariable UUID id) {
    return ResponseEntity.ok(informationRequestService.resendNotification(id));
  }

  @GetMapping("/api/customers/{customerId}/information-requests")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<InformationRequestResponse>> listByCustomer(
      @PathVariable UUID customerId) {
    return ResponseEntity.ok(informationRequestService.listByCustomer(customerId));
  }

  @GetMapping("/api/projects/{projectId}/information-requests")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<List<InformationRequestResponse>> listByProject(
      @PathVariable UUID projectId) {
    return ResponseEntity.ok(informationRequestService.listByProject(projectId));
  }

  @GetMapping("/api/information-requests/summary")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<DashboardSummaryResponse> getSummary() {
    return ResponseEntity.ok(informationRequestService.getDashboardSummary());
  }
}

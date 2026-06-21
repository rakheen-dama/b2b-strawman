package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.crm.dto.DealResponse;
import io.b2mash.b2b.b2bstrawman.crm.dto.DealUpdateRequest;
import io.b2mash.b2b.b2bstrawman.crm.dto.IntakeRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin HTTP adapter for {@link Deal} CRUD + intake + filtered list (Phase 80, slice 574A). Each
 * method delegates to exactly one service call and wraps the result in a {@link ResponseEntity}.
 * Capability gating uses {@link RequiresCapability} (the io.b2mash custom annotation, String
 * value).
 */
@RestController
public class DealController {

  private final DealService dealService;
  private final DealIntakeService dealIntakeService;

  public DealController(DealService dealService, DealIntakeService dealIntakeService) {
    this.dealService = dealService;
    this.dealIntakeService = dealIntakeService;
  }

  @PostMapping("/api/deals/intake")
  @RequiresCapability("MANAGE_DEALS")
  public ResponseEntity<DealResponse> intake(@Valid @RequestBody IntakeRequest request) {
    var deal = dealIntakeService.intake(request, RequestScopes.requireMemberId());
    return ResponseEntity.created(URI.create("/api/deals/" + deal.id())).body(deal);
  }

  @PostMapping("/api/deals")
  @RequiresCapability("MANAGE_DEALS")
  public ResponseEntity<DealResponse> createDeal(@Valid @RequestBody CreateDealRequest request) {
    var deal =
        dealService.createDeal(
            request.customerId(),
            request.title(),
            request.stageId(),
            request.valueAmount(),
            request.ownerId(),
            request.source(),
            request.expectedCloseDate(),
            RequestScopes.requireMemberId());
    return ResponseEntity.created(URI.create("/api/deals/" + deal.id())).body(deal);
  }

  @GetMapping("/api/deals")
  @RequiresCapability("VIEW_DEALS")
  public ResponseEntity<Page<DealResponse>> listDeals(
      @RequestParam(required = false) UUID stageId,
      @RequestParam(required = false) UUID ownerId,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) DealStatus status,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fromDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate toDate,
      Pageable pageable) {
    return ResponseEntity.ok(
        dealService.listDeals(
            stageId, ownerId, customerId, status, source, fromDate, toDate, pageable));
  }

  @GetMapping("/api/deals/{id}")
  @RequiresCapability("VIEW_DEALS")
  public ResponseEntity<DealResponse> getDeal(@PathVariable UUID id) {
    return ResponseEntity.ok(dealService.getDeal(id));
  }

  @PutMapping("/api/deals/{id}")
  @RequiresCapability("MANAGE_DEALS")
  public ResponseEntity<DealResponse> updateDeal(
      @PathVariable UUID id, @Valid @RequestBody DealUpdateRequest request) {
    return ResponseEntity.ok(dealService.updateDeal(id, request));
  }

  @DeleteMapping("/api/deals/{id}")
  @RequiresCapability("MANAGE_DEALS")
  public ResponseEntity<Void> deleteDeal(@PathVariable UUID id) {
    dealService.deleteDeal(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Create-against-existing-customer request. {@code customerId} and {@code title} are required;
   * the rest are optional (stage defaults to the first OPEN stage, owner defaults to the acting
   * member).
   */
  public record CreateDealRequest(
      @jakarta.validation.constraints.NotNull(message = "customerId is required") UUID customerId,
      @jakarta.validation.constraints.NotBlank(message = "title is required")
          @jakarta.validation.constraints.Size(
              max = 200,
              message = "title must not exceed 200 characters")
          String title,
      UUID stageId,
      java.math.BigDecimal valueAmount,
      UUID ownerId,
      String source,
      LocalDate expectedCloseDate) {}
}

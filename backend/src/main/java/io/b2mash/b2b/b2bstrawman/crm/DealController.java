package io.b2mash.b2b.b2bstrawman.crm;

import io.b2mash.b2b.b2bstrawman.crm.dto.CreateDealProposalRequest;
import io.b2mash.b2b.b2bstrawman.crm.dto.CreateDealRequest;
import io.b2mash.b2b.b2bstrawman.crm.dto.DealResponse;
import io.b2mash.b2b.b2bstrawman.crm.dto.DealUpdateRequest;
import io.b2mash.b2b.b2bstrawman.crm.dto.IntakeRequest;
import io.b2mash.b2b.b2bstrawman.crm.dto.LinkedProposalDto;
import io.b2mash.b2b.b2bstrawman.crm.dto.TransitionRequest;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
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
  private final DealTransitionService dealTransitionService;
  private final DealProposalService dealProposalService;

  public DealController(
      DealService dealService,
      DealIntakeService dealIntakeService,
      DealTransitionService dealTransitionService,
      DealProposalService dealProposalService) {
    this.dealService = dealService;
    this.dealIntakeService = dealIntakeService;
    this.dealTransitionService = dealTransitionService;
    this.dealProposalService = dealProposalService;
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
      @RequestParam(required = false) List<String> tags,
      @RequestParam(required = false) UUID view,
      Pageable pageable) {
    return ResponseEntity.ok(
        dealService.listDeals(
            stageId, ownerId, customerId, status, source, fromDate, toDate, tags, view, pageable));
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

  @PostMapping("/api/deals/{id}/transition")
  @RequiresCapability("MANAGE_DEALS")
  public ResponseEntity<DealResponse> transition(
      @PathVariable UUID id, @Valid @RequestBody TransitionRequest request) {
    return ResponseEntity.ok(dealTransitionService.transition(id, request));
  }

  // === Deal↔Proposal link (Phase 80, slice 576A) ===

  @GetMapping("/api/deals/{id}/proposals")
  @RequiresCapability("VIEW_DEALS")
  public ResponseEntity<List<LinkedProposalDto>> listProposals(@PathVariable UUID id) {
    return ResponseEntity.ok(dealProposalService.listForDeal(id));
  }

  @PostMapping("/api/deals/{id}/proposals")
  @RequiresCapability("MANAGE_DEALS")
  public ResponseEntity<LinkedProposalDto> createProposalFromDeal(
      @PathVariable UUID id, @Valid @RequestBody CreateDealProposalRequest request) {
    var dto = dealProposalService.createFromDeal(id, request, RequestScopes.requireMemberId());
    return ResponseEntity.created(URI.create("/api/proposals/" + dto.id())).body(dto);
  }

  @PostMapping("/api/deals/{id}/proposals/{proposalId}/link")
  @RequiresCapability("MANAGE_DEALS")
  public ResponseEntity<Void> linkProposal(@PathVariable UUID id, @PathVariable UUID proposalId) {
    dealProposalService.linkExisting(id, proposalId);
    return ResponseEntity.noContent().build();
  }
}

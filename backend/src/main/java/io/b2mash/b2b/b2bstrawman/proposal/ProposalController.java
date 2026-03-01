package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.proposal.dto.MilestoneRequest;
import io.b2mash.b2b.b2bstrawman.proposal.dto.ProposalFilterCriteria;
import io.b2mash.b2b.b2bstrawman.proposal.dto.ProposalStats;
import io.b2mash.b2b.b2bstrawman.proposal.dto.TeamMemberRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProposalController {

  private final ProposalService proposalService;

  public ProposalController(ProposalService proposalService) {
    this.proposalService = proposalService;
  }

  // --- 231.9: CRUD endpoints ---

  @PostMapping("/api/proposals")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProposalResponse> createProposal(
      @Valid @RequestBody CreateProposalRequest request) {
    UUID createdById = RequestScopes.requireMemberId();
    var proposal =
        proposalService.createProposal(
            request.title(),
            request.customerId(),
            request.feeModel(),
            createdById,
            request.portalContactId(),
            request.fixedFeeAmount(),
            request.fixedFeeCurrency(),
            request.hourlyRateNote(),
            request.retainerAmount(),
            request.retainerCurrency(),
            request.retainerHoursIncluded(),
            request.contentJson(),
            request.projectTemplateId(),
            request.expiresAt());
    return ResponseEntity.created(URI.create("/api/proposals/" + proposal.getId()))
        .body(ProposalResponse.from(proposal));
  }

  @GetMapping("/api/proposals")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Page<ProposalResponse>> listProposals(
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false) ProposalStatus status,
      @RequestParam(required = false) FeeModel feeModel,
      @RequestParam(required = false) UUID createdById,
      Pageable pageable) {
    var criteria = new ProposalFilterCriteria(customerId, status, feeModel, createdById);
    var page = proposalService.listProposals(criteria, pageable);
    return ResponseEntity.ok(page.map(ProposalResponse::from));
  }

  @GetMapping("/api/proposals/{id}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProposalResponse> getProposal(@PathVariable UUID id) {
    var proposal = proposalService.getProposal(id);
    return ResponseEntity.ok(ProposalResponse.from(proposal));
  }

  @PutMapping("/api/proposals/{id}")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProposalResponse> updateProposal(
      @PathVariable UUID id, @Valid @RequestBody UpdateProposalRequest request) {
    var proposal =
        proposalService.updateProposal(
            id,
            request.title(),
            request.customerId(),
            request.portalContactId(),
            request.feeModel(),
            request.fixedFeeAmount(),
            request.fixedFeeCurrency(),
            request.hourlyRateNote(),
            request.retainerAmount(),
            request.retainerCurrency(),
            request.retainerHoursIncluded(),
            request.contentJson(),
            request.projectTemplateId(),
            request.expiresAt());
    return ResponseEntity.ok(ProposalResponse.from(proposal));
  }

  @DeleteMapping("/api/proposals/{id}")
  @PreAuthorize("hasRole('ORG_OWNER')")
  public ResponseEntity<Void> deleteProposal(@PathVariable UUID id) {
    proposalService.deleteProposal(id);
    return ResponseEntity.noContent().build();
  }

  // --- 231.10: Milestone and team endpoints ---

  @PutMapping("/api/proposals/{id}/milestones")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<MilestoneResponse>> replaceMilestones(
      @PathVariable UUID id, @Valid @RequestBody List<MilestoneRequest> milestones) {
    var saved = proposalService.replaceMilestones(id, milestones);
    return ResponseEntity.ok(saved.stream().map(MilestoneResponse::from).toList());
  }

  @PutMapping("/api/proposals/{id}/team")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<TeamMemberResponse>> replaceTeamMembers(
      @PathVariable UUID id, @Valid @RequestBody List<TeamMemberRequest> members) {
    var saved = proposalService.replaceTeamMembers(id, members);
    return ResponseEntity.ok(saved.stream().map(TeamMemberResponse::from).toList());
  }

  // --- 231.11: Stats and customer-scoped endpoints ---

  @GetMapping("/api/proposals/stats")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProposalStats> getStats() {
    return ResponseEntity.ok(proposalService.getStats());
  }

  @GetMapping("/api/customers/{customerId}/proposals")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Page<ProposalResponse>> listCustomerProposals(
      @PathVariable UUID customerId, Pageable pageable) {
    var page = proposalService.listByCustomer(customerId, pageable);
    return ResponseEntity.ok(page.map(ProposalResponse::from));
  }

  // --- 231.12: DTOs ---

  public record CreateProposalRequest(
      @NotBlank(message = "title is required")
          @Size(max = 200, message = "title must not exceed 200 characters")
          String title,
      @NotNull(message = "customerId is required") UUID customerId,
      @NotNull(message = "feeModel is required") FeeModel feeModel,
      UUID portalContactId,
      BigDecimal fixedFeeAmount,
      String fixedFeeCurrency,
      String hourlyRateNote,
      BigDecimal retainerAmount,
      String retainerCurrency,
      BigDecimal retainerHoursIncluded,
      Map<String, Object> contentJson,
      UUID projectTemplateId,
      Instant expiresAt) {}

  public record UpdateProposalRequest(
      @Size(min = 1, max = 200, message = "title must be between 1 and 200 characters")
          String title,
      UUID customerId,
      UUID portalContactId,
      FeeModel feeModel,
      BigDecimal fixedFeeAmount,
      String fixedFeeCurrency,
      String hourlyRateNote,
      BigDecimal retainerAmount,
      String retainerCurrency,
      BigDecimal retainerHoursIncluded,
      Map<String, Object> contentJson,
      UUID projectTemplateId,
      Instant expiresAt) {}

  public record ProposalResponse(
      UUID id,
      String proposalNumber,
      String title,
      UUID customerId,
      UUID portalContactId,
      ProposalStatus status,
      FeeModel feeModel,
      BigDecimal fixedFeeAmount,
      String fixedFeeCurrency,
      String hourlyRateNote,
      BigDecimal retainerAmount,
      String retainerCurrency,
      BigDecimal retainerHoursIncluded,
      Map<String, Object> contentJson,
      UUID projectTemplateId,
      Instant sentAt,
      Instant expiresAt,
      Instant acceptedAt,
      Instant declinedAt,
      String declineReason,
      UUID createdProjectId,
      UUID createdRetainerId,
      UUID createdById,
      Instant createdAt,
      Instant updatedAt) {

    public static ProposalResponse from(Proposal proposal) {
      return new ProposalResponse(
          proposal.getId(),
          proposal.getProposalNumber(),
          proposal.getTitle(),
          proposal.getCustomerId(),
          proposal.getPortalContactId(),
          proposal.getStatus(),
          proposal.getFeeModel(),
          proposal.getFixedFeeAmount(),
          proposal.getFixedFeeCurrency(),
          proposal.getHourlyRateNote(),
          proposal.getRetainerAmount(),
          proposal.getRetainerCurrency(),
          proposal.getRetainerHoursIncluded(),
          proposal.getContentJson(),
          proposal.getProjectTemplateId(),
          proposal.getSentAt(),
          proposal.getExpiresAt(),
          proposal.getAcceptedAt(),
          proposal.getDeclinedAt(),
          proposal.getDeclineReason(),
          proposal.getCreatedProjectId(),
          proposal.getCreatedRetainerId(),
          proposal.getCreatedById(),
          proposal.getCreatedAt(),
          proposal.getUpdatedAt());
    }
  }

  public record MilestoneResponse(
      UUID id,
      UUID proposalId,
      String description,
      BigDecimal percentage,
      int relativeDueDays,
      int sortOrder,
      UUID invoiceId,
      Instant createdAt,
      Instant updatedAt) {

    public static MilestoneResponse from(ProposalMilestone milestone) {
      return new MilestoneResponse(
          milestone.getId(),
          milestone.getProposalId(),
          milestone.getDescription(),
          milestone.getPercentage(),
          milestone.getRelativeDueDays(),
          milestone.getSortOrder(),
          milestone.getInvoiceId(),
          milestone.getCreatedAt(),
          milestone.getUpdatedAt());
    }
  }

  public record TeamMemberResponse(
      UUID id, UUID proposalId, UUID memberId, String role, int sortOrder) {

    public static TeamMemberResponse from(ProposalTeamMember teamMember) {
      return new TeamMemberResponse(
          teamMember.getId(),
          teamMember.getProposalId(),
          teamMember.getMemberId(),
          teamMember.getRole(),
          teamMember.getSortOrder());
    }
  }
}

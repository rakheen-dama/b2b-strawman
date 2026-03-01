package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.event.ProposalSentEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.proposal.dto.MilestoneRequest;
import io.b2mash.b2b.b2bstrawman.proposal.dto.ProposalFilterCriteria;
import io.b2mash.b2b.b2bstrawman.proposal.dto.ProposalStats;
import io.b2mash.b2b.b2bstrawman.proposal.dto.TeamMemberRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposalService {

  private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

  private final ProposalRepository proposalRepository;
  private final ProposalMilestoneRepository milestoneRepository;
  private final ProposalTeamMemberRepository teamMemberRepository;
  private final ProposalNumberService proposalNumberService;
  private final CustomerRepository customerRepository;
  private final MemberRepository memberRepository;
  private final PortalContactRepository portalContactRepository;
  private final ProposalPortalSyncService proposalPortalSyncService;
  private final ApplicationEventPublisher eventPublisher;
  private final MemberNameResolver memberNameResolver;
  private final AuditService auditService;
  private final NotificationService notificationService;

  public ProposalService(
      ProposalRepository proposalRepository,
      ProposalMilestoneRepository milestoneRepository,
      ProposalTeamMemberRepository teamMemberRepository,
      ProposalNumberService proposalNumberService,
      CustomerRepository customerRepository,
      MemberRepository memberRepository,
      PortalContactRepository portalContactRepository,
      ProposalPortalSyncService proposalPortalSyncService,
      ApplicationEventPublisher eventPublisher,
      MemberNameResolver memberNameResolver,
      AuditService auditService,
      NotificationService notificationService) {
    this.proposalRepository = proposalRepository;
    this.milestoneRepository = milestoneRepository;
    this.teamMemberRepository = teamMemberRepository;
    this.proposalNumberService = proposalNumberService;
    this.customerRepository = customerRepository;
    this.memberRepository = memberRepository;
    this.portalContactRepository = portalContactRepository;
    this.proposalPortalSyncService = proposalPortalSyncService;
    this.eventPublisher = eventPublisher;
    this.memberNameResolver = memberNameResolver;
    this.auditService = auditService;
    this.notificationService = notificationService;
  }

  // --- 231.1: createProposal ---

  @Transactional
  public Proposal createProposal(
      String title,
      UUID customerId,
      FeeModel feeModel,
      UUID createdById,
      UUID portalContactId,
      BigDecimal fixedFeeAmount,
      String fixedFeeCurrency,
      String hourlyRateNote,
      BigDecimal retainerAmount,
      String retainerCurrency,
      BigDecimal retainerHoursIncluded,
      Map<String, Object> contentJson,
      UUID projectTemplateId,
      Instant expiresAt) {

    // Validate customer exists
    customerRepository
        .findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Validate fee fields per model
    validateFeeConfiguration(feeModel, fixedFeeAmount, retainerAmount);

    // Allocate proposal number
    String proposalNumber = proposalNumberService.allocateNumber();

    // Create entity
    var proposal = new Proposal(proposalNumber, title, customerId, feeModel, createdById);

    // Set optional fields (entity setters call requireEditable() but we're DRAFT so it's fine)
    if (portalContactId != null) proposal.setPortalContactId(portalContactId);
    if (fixedFeeAmount != null) proposal.setFixedFeeAmount(fixedFeeAmount);
    if (fixedFeeCurrency != null) proposal.setFixedFeeCurrency(fixedFeeCurrency);
    if (hourlyRateNote != null) proposal.setHourlyRateNote(hourlyRateNote);
    if (retainerAmount != null) proposal.setRetainerAmount(retainerAmount);
    if (retainerCurrency != null) proposal.setRetainerCurrency(retainerCurrency);
    if (retainerHoursIncluded != null) proposal.setRetainerHoursIncluded(retainerHoursIncluded);
    if (contentJson != null) proposal.setContentJson(contentJson);
    if (projectTemplateId != null) proposal.setProjectTemplateId(projectTemplateId);
    if (expiresAt != null) proposal.setExpiresAt(expiresAt);

    var saved = proposalRepository.save(proposal);

    // Audit
    var auditDetails = new LinkedHashMap<String, Object>();
    auditDetails.put("proposal_number", proposalNumber);
    auditDetails.put("title", title);
    auditDetails.put("customer_id", customerId.toString());
    auditDetails.put("fee_model", feeModel.name());
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("proposal.created")
            .entityType("proposal")
            .entityId(saved.getId())
            .details(auditDetails)
            .build());

    log.info("Created proposal {} ({}) for customer {}", saved.getId(), proposalNumber, customerId);
    return saved;
  }

  // --- 231.2: getProposal, listProposals, listByCustomer ---

  @Transactional(readOnly = true)
  public Proposal getProposal(UUID proposalId) {
    return proposalRepository
        .findById(proposalId)
        .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));
  }

  @Transactional(readOnly = true)
  public List<ProposalMilestone> getMilestones(UUID proposalId) {
    return milestoneRepository.findByProposalIdOrderBySortOrder(proposalId);
  }

  @Transactional(readOnly = true)
  public List<ProposalTeamMember> getTeamMembers(UUID proposalId) {
    return teamMemberRepository.findByProposalIdOrderBySortOrder(proposalId);
  }

  @Transactional(readOnly = true)
  public Page<Proposal> listProposals(ProposalFilterCriteria criteria, Pageable pageable) {
    return proposalRepository.findFiltered(
        criteria.customerId(),
        criteria.status(),
        criteria.feeModel(),
        criteria.createdById(),
        pageable);
  }

  @Transactional(readOnly = true)
  public Page<Proposal> listByCustomer(UUID customerId, Pageable pageable) {
    return proposalRepository.findByCustomerId(customerId, pageable);
  }

  // --- 231.3: updateProposal ---

  @Transactional
  public Proposal updateProposal(
      UUID proposalId,
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
      Instant expiresAt) {

    var proposal =
        proposalRepository
            .findById(proposalId)
            .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));

    // Guard: DRAFT only (409)
    requireDraft(proposal);

    // Validate customer if changed
    if (customerId != null) {
      customerRepository
          .findById(customerId)
          .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    }

    // Validate fee configuration if feeModel is being changed
    FeeModel effectiveFeeModel = feeModel != null ? feeModel : proposal.getFeeModel();
    BigDecimal effectiveFixedAmount =
        fixedFeeAmount != null ? fixedFeeAmount : proposal.getFixedFeeAmount();
    BigDecimal effectiveRetainerAmount =
        retainerAmount != null ? retainerAmount : proposal.getRetainerAmount();
    validateFeeConfiguration(effectiveFeeModel, effectiveFixedAmount, effectiveRetainerAmount);

    // Clear stale fee fields when switching fee models
    if (feeModel != null && feeModel != proposal.getFeeModel()) {
      switch (proposal.getFeeModel()) {
        case FIXED -> {
          proposal.setFixedFeeAmount(null);
          proposal.setFixedFeeCurrency(null);
        }
        case RETAINER -> {
          proposal.setRetainerAmount(null);
          proposal.setRetainerCurrency(null);
          proposal.setRetainerHoursIncluded(null);
        }
        case HOURLY -> proposal.setHourlyRateNote(null);
      }
      proposal.setFeeModel(feeModel);
    }

    // Update mutable fields via guarded setters
    if (title != null) proposal.setTitle(title);
    if (customerId != null) proposal.setCustomerId(customerId);
    if (portalContactId != null) proposal.setPortalContactId(portalContactId);
    if (fixedFeeAmount != null) proposal.setFixedFeeAmount(fixedFeeAmount);
    if (fixedFeeCurrency != null) proposal.setFixedFeeCurrency(fixedFeeCurrency);
    if (hourlyRateNote != null) proposal.setHourlyRateNote(hourlyRateNote);
    if (retainerAmount != null) proposal.setRetainerAmount(retainerAmount);
    if (retainerCurrency != null) proposal.setRetainerCurrency(retainerCurrency);
    if (retainerHoursIncluded != null) proposal.setRetainerHoursIncluded(retainerHoursIncluded);
    if (contentJson != null) proposal.setContentJson(contentJson);
    if (projectTemplateId != null) proposal.setProjectTemplateId(projectTemplateId);
    if (expiresAt != null) proposal.setExpiresAt(expiresAt);

    var saved = proposalRepository.save(proposal);

    // Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("proposal.updated")
            .entityType("proposal")
            .entityId(saved.getId())
            .details(Map.of("proposal_number", saved.getProposalNumber()))
            .build());

    log.info("Updated proposal {}", proposalId);
    return saved;
  }

  // --- 231.4: deleteProposal ---

  @Transactional
  public void deleteProposal(UUID proposalId) {
    var proposal =
        proposalRepository
            .findById(proposalId)
            .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));

    // Guard: DRAFT only (409)
    requireDraft(proposal);

    // CASCADE handles milestones and team members in DB
    proposalRepository.deleteById(proposalId);

    // Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("proposal.deleted")
            .entityType("proposal")
            .entityId(proposalId)
            .details(
                Map.of(
                    "proposal_number", proposal.getProposalNumber(),
                    "title", proposal.getTitle()))
            .build());

    log.info("Deleted proposal {}", proposalId);
  }

  // --- 231.5: replaceMilestones ---

  @Transactional
  public List<ProposalMilestone> replaceMilestones(
      UUID proposalId, List<MilestoneRequest> milestones) {
    var proposal =
        proposalRepository
            .findById(proposalId)
            .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));

    requireDraft(proposal);

    // Must be FIXED fee model
    if (proposal.getFeeModel() != FeeModel.FIXED) {
      throw new InvalidStateException(
          "Invalid fee model", "Milestones are only valid for FIXED fee model proposals");
    }

    // Validate milestones
    if (milestones != null && !milestones.isEmpty()) {
      for (var m : milestones) {
        if (m.description() == null || m.description().isBlank()) {
          throw new InvalidStateException(
              "Invalid milestone", "Milestone description must not be blank");
        }
        if (m.percentage() == null || m.percentage().compareTo(BigDecimal.ZERO) <= 0) {
          throw new InvalidStateException(
              "Invalid milestone", "Milestone percentage must be positive");
        }
      }

      BigDecimal total =
          milestones.stream()
              .map(MilestoneRequest::percentage)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      if (total.compareTo(new BigDecimal("100.00")) != 0) {
        throw new InvalidStateException(
            "Invalid milestone percentages",
            "Milestone percentages must sum to exactly 100.00, got " + total);
      }
    }

    // Delete existing and flush to prevent reordering issues
    milestoneRepository.deleteByProposalId(proposalId);
    milestoneRepository.flush();

    // Create new
    if (milestones == null || milestones.isEmpty()) {
      return List.of();
    }

    var entities = new ArrayList<ProposalMilestone>();
    for (int i = 0; i < milestones.size(); i++) {
      var req = milestones.get(i);
      entities.add(
          new ProposalMilestone(
              proposalId, req.description(), req.percentage(), req.relativeDueDays(), i));
    }

    var saved = milestoneRepository.saveAll(entities);
    log.info("Replaced {} milestones for proposal {}", saved.size(), proposalId);
    return saved;
  }

  // --- 231.6: replaceTeamMembers ---

  @Transactional
  public List<ProposalTeamMember> replaceTeamMembers(
      UUID proposalId, List<TeamMemberRequest> members) {
    var proposal =
        proposalRepository
            .findById(proposalId)
            .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));

    requireDraft(proposal);

    // Validate members
    if (members != null && !members.isEmpty()) {
      var memberIds = members.stream().map(TeamMemberRequest::memberId).toList();

      // Check for null member IDs
      if (memberIds.contains(null)) {
        throw new InvalidStateException("Invalid team member", "Member ID must not be null");
      }

      // Check for duplicate member IDs
      var uniqueIds = new HashSet<>(memberIds);
      if (uniqueIds.size() != memberIds.size()) {
        throw new InvalidStateException("Invalid team members", "Duplicate member IDs in team");
      }

      // Batch-validate all members exist (avoids N+1)
      var foundMembers = memberRepository.findAllById(memberIds);
      if (foundMembers.size() != memberIds.size()) {
        var foundIds = foundMembers.stream().map(m -> m.getId()).collect(Collectors.toSet());
        var missing =
            memberIds.stream().filter(id -> !foundIds.contains(id)).findFirst().orElseThrow();
        throw new ResourceNotFoundException("Member", missing);
      }
    }

    // Delete existing and flush to prevent reordering issues
    teamMemberRepository.deleteByProposalId(proposalId);
    teamMemberRepository.flush();

    // Create new
    if (members == null || members.isEmpty()) {
      return List.of();
    }

    var entities = new ArrayList<ProposalTeamMember>();
    for (int i = 0; i < members.size(); i++) {
      var req = members.get(i);
      entities.add(new ProposalTeamMember(proposalId, req.memberId(), req.role(), i));
    }

    var saved = teamMemberRepository.saveAll(entities);
    log.info("Replaced {} team members for proposal {}", saved.size(), proposalId);
    return saved;
  }

  // --- 231.7: getStats ---

  @Transactional(readOnly = true)
  public ProposalStats getStats() {
    long totalDraft = proposalRepository.countByStatus(ProposalStatus.DRAFT);
    long totalSent = proposalRepository.countByStatus(ProposalStatus.SENT);
    long totalAccepted = proposalRepository.countByStatus(ProposalStatus.ACCEPTED);
    long totalDeclined = proposalRepository.countByStatus(ProposalStatus.DECLINED);
    long totalExpired = proposalRepository.countByStatus(ProposalStatus.EXPIRED);

    double conversionRate = 0.0;
    long decided = totalAccepted + totalDeclined;
    if (decided > 0) {
      conversionRate = (double) totalAccepted / decided * 100.0;
    }

    Double avgDays = proposalRepository.averageDaysToAccept();
    double averageDaysToAccept = avgDays != null ? avgDays : 0.0;

    return new ProposalStats(
        totalDraft,
        totalSent,
        totalAccepted,
        totalDeclined,
        totalExpired,
        conversionRate,
        averageDaysToAccept);
  }

  // --- 232.6–232.9: sendProposal ---

  @Transactional
  public Proposal sendProposal(UUID proposalId, UUID portalContactId) {
    // 1. Load and validate proposal
    var proposal =
        proposalRepository
            .findById(proposalId)
            .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));
    requireDraft(proposal);

    // 2. Validate content is not empty
    if (proposal.getContentJson() == null || proposal.getContentJson().isEmpty()) {
      throw new InvalidStateException(
          "Invalid proposal content", "Proposal content must not be empty");
    }

    // 3. Validate fee configuration
    validateFeeConfiguration(
        proposal.getFeeModel(), proposal.getFixedFeeAmount(), proposal.getRetainerAmount());

    // 4. Validate milestones sum to 100 if FIXED + milestones exist
    if (proposal.getFeeModel() == FeeModel.FIXED) {
      var milestones = milestoneRepository.findByProposalIdOrderBySortOrder(proposalId);
      if (!milestones.isEmpty()) {
        BigDecimal total =
            milestones.stream()
                .map(ProposalMilestone::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(new BigDecimal("100.00")) != 0) {
          throw new InvalidStateException(
              "Invalid milestone percentages",
              "Milestone percentages must sum to exactly 100.00, got " + total);
        }
      }
    }

    // 5. Validate portal contact
    var contact =
        portalContactRepository
            .findById(portalContactId)
            .orElseThrow(() -> new ResourceNotFoundException("PortalContact", portalContactId));
    if (!contact.getCustomerId().equals(proposal.getCustomerId())) {
      throw new InvalidStateException(
          "Portal contact mismatch", "Portal contact does not belong to the proposal's customer");
    }

    // 6. Transition
    proposal.markSent(portalContactId);

    // 7. Save
    var saved = proposalRepository.save(proposal);

    // Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("proposal.sent")
            .entityType("proposal")
            .entityId(saved.getId())
            .details(
                Map.of(
                    "proposal_number", saved.getProposalNumber(),
                    "customer_id", saved.getCustomerId().toString(),
                    "portal_contact_id", portalContactId.toString()))
            .build());

    // 8. Publish event — portal sync and notifications run AFTER_COMMIT via event handlers
    UUID memberId = RequestScopes.requireMemberId();
    String actorName = memberNameResolver.resolveName(memberId);
    eventPublisher.publishEvent(
        new ProposalSentEvent(
            "proposal.sent",
            "proposal",
            saved.getId(),
            null,
            memberId,
            actorName,
            RequestScopes.requireTenantId(),
            RequestScopes.requireOrgId(),
            Instant.now(),
            Map.of(
                "proposal_number",
                saved.getProposalNumber(),
                "customer_id",
                saved.getCustomerId().toString(),
                "contact_name",
                contact.getDisplayName() != null ? contact.getDisplayName() : "")));

    log.info("Sent proposal {} to contact {}", proposalId, portalContactId);
    return saved;
  }

  // --- 232.12: withdrawProposal ---

  @Transactional
  public Proposal withdrawProposal(UUID proposalId) {
    var proposal =
        proposalRepository
            .findById(proposalId)
            .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));

    // Only SENT proposals can be withdrawn
    proposal.markWithdrawn();

    // Update portal read model status
    proposalPortalSyncService.updatePortalProposalStatus(proposalId, "DRAFT");

    // Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("proposal.withdrawn")
            .entityType("proposal")
            .entityId(proposalId)
            .details(Map.of("proposal_number", proposal.getProposalNumber()))
            .build());

    var saved = proposalRepository.save(proposal);
    log.info("Withdrew proposal {}", proposalId);
    return saved;
  }

  // --- 234.6: declineProposal ---

  @Transactional
  public Proposal declineProposal(UUID proposalId, String reason) {
    var proposal =
        proposalRepository
            .findById(proposalId)
            .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));

    // Guard: only SENT proposals can be declined (markDeclined throws InvalidStateException)
    proposal.markDeclined(reason);

    var saved = proposalRepository.save(proposal);

    // Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("proposal.declined")
            .entityType("proposal")
            .entityId(proposalId)
            .details(
                Map.of(
                    "proposal_number",
                    proposal.getProposalNumber(),
                    "reason",
                    reason != null ? reason : ""))
            .build());

    // Portal sync
    proposalPortalSyncService.updatePortalProposalStatus(proposalId, "DECLINED");

    // Notify proposal creator (client-initiated decline)
    notificationService.createNotification(
        proposal.getCreatedById(),
        "PROPOSAL_DECLINED",
        "Proposal %s was declined".formatted(proposal.getProposalNumber()),
        reason != null ? "Reason: %s".formatted(reason) : "No reason provided",
        "PROPOSAL",
        proposalId,
        null);

    log.info("Declined proposal {} with reason: {}", proposalId, reason);
    return saved;
  }

  // --- Private helpers ---

  private void requireDraft(Proposal proposal) {
    if (!proposal.isEditable()) {
      throw new ResourceConflictException(
          "Proposal not editable", "Cannot modify proposal in status " + proposal.getStatus());
    }
  }

  private void validateFeeConfiguration(
      FeeModel feeModel, BigDecimal fixedFeeAmount, BigDecimal retainerAmount) {
    switch (feeModel) {
      case FIXED -> {
        if (fixedFeeAmount == null || fixedFeeAmount.compareTo(BigDecimal.ZERO) <= 0) {
          throw new InvalidStateException(
              "Invalid fee configuration", "FIXED fee model requires fixedFeeAmount > 0");
        }
      }
      case RETAINER -> {
        if (retainerAmount == null || retainerAmount.compareTo(BigDecimal.ZERO) <= 0) {
          throw new InvalidStateException(
              "Invalid fee configuration", "RETAINER fee model requires retainerAmount > 0");
        }
      }
      case HOURLY -> {
        // No amount requirement
      }
    }
  }
}

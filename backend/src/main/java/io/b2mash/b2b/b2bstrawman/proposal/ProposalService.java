package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.proposal.dto.MilestoneRequest;
import io.b2mash.b2b.b2bstrawman.proposal.dto.ProposalFilterCriteria;
import io.b2mash.b2b.b2bstrawman.proposal.dto.ProposalStats;
import io.b2mash.b2b.b2bstrawman.proposal.dto.TeamMemberRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  public ProposalService(
      ProposalRepository proposalRepository,
      ProposalMilestoneRepository milestoneRepository,
      ProposalTeamMemberRepository teamMemberRepository,
      ProposalNumberService proposalNumberService,
      CustomerRepository customerRepository,
      MemberRepository memberRepository) {
    this.proposalRepository = proposalRepository;
    this.milestoneRepository = milestoneRepository;
    this.teamMemberRepository = teamMemberRepository;
    this.proposalNumberService = proposalNumberService;
    this.customerRepository = customerRepository;
    this.memberRepository = memberRepository;
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

    // Update mutable fields via guarded setters
    if (title != null) proposal.setTitle(title);
    if (customerId != null) proposal.setCustomerId(customerId);
    if (portalContactId != null) proposal.setPortalContactId(portalContactId);
    if (feeModel != null) proposal.setFeeModel(feeModel);
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

    // Validate percentages sum to 100
    if (milestones != null && !milestones.isEmpty()) {
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

    // Delete existing
    milestoneRepository.deleteByProposalId(proposalId);

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

    // Validate each member exists
    if (members != null) {
      for (var member : members) {
        memberRepository
            .findById(member.memberId())
            .orElseThrow(() -> new ResourceNotFoundException("Member", member.memberId()));
      }
    }

    // Delete existing
    teamMemberRepository.deleteByProposalId(proposalId);

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

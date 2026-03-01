package io.b2mash.b2b.b2bstrawman.proposal;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.invoice.Invoice;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineType;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectMemberService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateService;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.InstantiateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates proposal acceptance: status transition, customer lifecycle, project creation, team
 * assignment, and billing setup — all within a single transaction (ADR-125).
 */
@Service
public class ProposalOrchestrationService {

  private static final Logger log = LoggerFactory.getLogger(ProposalOrchestrationService.class);

  private final ProposalRepository proposalRepository;
  private final ProposalMilestoneRepository proposalMilestoneRepository;
  private final ProposalTeamMemberRepository proposalTeamMemberRepository;
  private final ProjectTemplateService projectTemplateService;
  private final ProjectService projectService;
  private final ProjectMemberService projectMemberService;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final CustomerRepository customerRepository;
  private final OrganizationRepository organizationRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;

  public ProposalOrchestrationService(
      ProposalRepository proposalRepository,
      ProposalMilestoneRepository proposalMilestoneRepository,
      ProposalTeamMemberRepository proposalTeamMemberRepository,
      ProjectTemplateService projectTemplateService,
      ProjectService projectService,
      ProjectMemberService projectMemberService,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      CustomerRepository customerRepository,
      OrganizationRepository organizationRepository,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher) {
    this.proposalRepository = proposalRepository;
    this.proposalMilestoneRepository = proposalMilestoneRepository;
    this.proposalTeamMemberRepository = proposalTeamMemberRepository;
    this.projectTemplateService = projectTemplateService;
    this.projectService = projectService;
    this.projectMemberService = projectMemberService;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.customerRepository = customerRepository;
    this.organizationRepository = organizationRepository;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
  }

  /**
   * Accepts a proposal and orchestrates all downstream entity creation within a single transaction.
   *
   * <p>Step order (per ADR-125 / lifecycle guard requirement):
   *
   * <ol>
   *   <li>Mark proposal as ACCEPTED
   *   <li>Transition customer lifecycle (PROSPECT -> ONBOARDING if applicable)
   *   <li>Create project (from template or bare)
   *   <li>Assign team members to the project
   *   <li>Create billing entities (FIXED fee: invoices; HOURLY: no-op)
   * </ol>
   *
   * @param proposalId the proposal to accept
   * @param portalContactId the portal contact who accepted the proposal
   * @return orchestration result with references to all created entities
   */
  @Transactional
  public OrchestrationResult acceptProposal(UUID proposalId, UUID portalContactId) {
    // Step 1: Update proposal status
    var proposal =
        proposalRepository
            .findById(proposalId)
            .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));

    if (proposal.getStatus() != ProposalStatus.SENT) {
      throw new InvalidStateException(
          "Invalid proposal state", "Cannot accept proposal in status " + proposal.getStatus());
    }

    if (!proposal.getPortalContactId().equals(portalContactId)) {
      throw new InvalidStateException(
          "Portal contact mismatch",
          "Portal contact does not match the proposal's assigned contact");
    }

    proposal.markAccepted();
    proposalRepository.save(proposal);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("proposal.accepted")
            .entityType("proposal")
            .entityId(proposalId)
            .details(
                Map.of(
                    "proposal_number", proposal.getProposalNumber(),
                    "customer_id", proposal.getCustomerId().toString()))
            .build());

    log.info("Proposal {} accepted by contact {}", proposalId, portalContactId);

    // Step 2: Transition customer lifecycle (before project creation — lifecycle guard blocks
    // PROSPECT)
    transitionCustomerLifecycle(proposal.getCustomerId(), proposal.getCreatedById());

    // Step 3: Create project
    var project = createProject(proposal);
    proposal.setCreatedProjectId(project.getId());
    proposalRepository.save(proposal);

    // Step 4: Assign team members
    var assignedMemberIds =
        assignTeamMembers(proposalId, project.getId(), proposal.getCreatedById());

    // Step 5: Create billing entities (FIXED fee only)
    var createdInvoiceIds = createBillingEntities(proposal, project.getId());

    log.info(
        "Orchestration complete for proposal {}: project={}, members={}, invoices={}",
        proposalId,
        project.getId(),
        assignedMemberIds.size(),
        createdInvoiceIds.size());

    return new OrchestrationResult(
        proposalId, project.getId(), assignedMemberIds, createdInvoiceIds);
  }

  // --- Step 2: Customer lifecycle transition ---

  private void transitionCustomerLifecycle(UUID customerId, UUID actorId) {
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    if (customer.getLifecycleStatus() == LifecycleStatus.PROSPECT) {
      customer.transitionLifecycleStatus(LifecycleStatus.ONBOARDING, actorId);
      customerRepository.save(customer);
      log.info("Transitioned customer {} from PROSPECT to ONBOARDING", customerId);
    }
    // ACTIVE or ONBOARDING — no-op
  }

  // --- Step 3: Project creation ---

  private Project createProject(Proposal proposal) {
    if (proposal.getProjectTemplateId() != null) {
      var request =
          new InstantiateTemplateRequest(proposal.getTitle(), proposal.getCustomerId(), null, null);
      return projectTemplateService.instantiateTemplate(
          proposal.getProjectTemplateId(), request, proposal.getCreatedById());
    } else {
      return projectService.createProject(
          proposal.getTitle(),
          null,
          proposal.getCreatedById(),
          null,
          null,
          proposal.getCustomerId(),
          null);
    }
  }

  // --- Step 4: Team assignment ---

  private List<UUID> assignTeamMembers(UUID proposalId, UUID projectId, UUID addedBy) {
    var teamMembers = proposalTeamMemberRepository.findByProposalIdOrderBySortOrder(proposalId);
    var assignedMemberIds = new ArrayList<UUID>();

    for (var teamMember : teamMembers) {
      try {
        projectMemberService.addMember(projectId, teamMember.getMemberId(), addedBy);
        assignedMemberIds.add(teamMember.getMemberId());
      } catch (ResourceConflictException e) {
        // Member already on the project (e.g., from template instantiation) — skip gracefully
        log.debug(
            "Member {} already on project {} — skipping", teamMember.getMemberId(), projectId);
        assignedMemberIds.add(teamMember.getMemberId());
      }
    }

    return assignedMemberIds;
  }

  // --- Step 5: Billing entity creation ---

  private List<UUID> createBillingEntities(Proposal proposal, UUID projectId) {
    if (proposal.getFeeModel() != FeeModel.FIXED) {
      // HOURLY and RETAINER (future) — no billing entities created at acceptance
      return List.of();
    }

    var milestones = proposalMilestoneRepository.findByProposalIdOrderBySortOrder(proposal.getId());

    // Look up customer and org for invoice creation
    var customer =
        customerRepository
            .findById(proposal.getCustomerId())
            .orElseThrow(() -> new ResourceNotFoundException("Customer", proposal.getCustomerId()));

    String orgId = RequestScopes.requireOrgId();
    var organization =
        organizationRepository
            .findByClerkOrgId(orgId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", orgId));
    String orgName = organization.getName();

    if (milestones.isEmpty()) {
      // Single invoice for full fixed fee amount
      var invoice =
          createDraftInvoice(
              proposal.getCustomerId(),
              proposal.getFixedFeeCurrency(),
              customer.getName(),
              customer.getEmail(),
              orgName,
              proposal.getCreatedById(),
              projectId,
              "Fixed fee — " + proposal.getTitle(),
              proposal.getFixedFeeAmount(),
              LocalDate.now().plusDays(30),
              0);
      return List.of(invoice.getId());
    }

    // One invoice per milestone
    var invoiceIds = new ArrayList<UUID>();
    for (int i = 0; i < milestones.size(); i++) {
      var milestone = milestones.get(i);
      BigDecimal amount =
          proposal
              .getFixedFeeAmount()
              .multiply(milestone.getPercentage())
              .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
      LocalDate dueDate = LocalDate.now().plusDays(milestone.getRelativeDueDays());

      var invoice =
          createDraftInvoice(
              proposal.getCustomerId(),
              proposal.getFixedFeeCurrency(),
              customer.getName(),
              customer.getEmail(),
              orgName,
              proposal.getCreatedById(),
              projectId,
              milestone.getDescription(),
              amount,
              dueDate,
              i);

      // Link milestone to its invoice
      milestone.setInvoiceId(invoice.getId());
      proposalMilestoneRepository.save(milestone);

      invoiceIds.add(invoice.getId());
    }

    return invoiceIds;
  }

  private Invoice createDraftInvoice(
      UUID customerId,
      String currency,
      String customerName,
      String customerEmail,
      String orgName,
      UUID createdBy,
      UUID projectId,
      String lineDescription,
      BigDecimal amount,
      LocalDate dueDate,
      int sortOrder) {

    var invoice =
        new Invoice(customerId, currency, customerName, customerEmail, null, orgName, createdBy);
    invoice = invoiceRepository.save(invoice);

    var line =
        new InvoiceLine(
            invoice.getId(), projectId, null, lineDescription, BigDecimal.ONE, amount, sortOrder);
    line.setLineType(InvoiceLineType.MANUAL);
    invoiceLineRepository.save(line);

    // Set subtotal/total on the invoice
    invoice.recalculateTotals(line.getAmount(), false, BigDecimal.ZERO, false);
    // Set due date
    invoice.updateDraft(dueDate, null, null, BigDecimal.ZERO);

    return invoiceRepository.save(invoice);
  }
}

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
import io.b2mash.b2b.b2bstrawman.notification.NotificationService;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectService;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateRepository;
import io.b2mash.b2b.b2bstrawman.projecttemplate.ProjectTemplateService;
import io.b2mash.b2b.b2bstrawman.projecttemplate.dto.InstantiateTemplateRequest;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerAgreementService;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerFrequency;
import io.b2mash.b2b.b2bstrawman.retainer.RetainerType;
import io.b2mash.b2b.b2bstrawman.retainer.RolloverPolicy;
import io.b2mash.b2b.b2bstrawman.retainer.dto.CreateRetainerRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final RetainerAgreementService retainerAgreementService;
  private final PrerequisiteService prerequisiteService;
  private final NotificationService notificationService;
  private final ProjectTemplateRepository templateRepository;

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
      ApplicationEventPublisher eventPublisher,
      RetainerAgreementService retainerAgreementService,
      PrerequisiteService prerequisiteService,
      NotificationService notificationService,
      ProjectTemplateRepository templateRepository) {
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
    this.retainerAgreementService = retainerAgreementService;
    this.prerequisiteService = prerequisiteService;
    this.notificationService = notificationService;
    this.templateRepository = templateRepository;
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
   *   <li>Create billing entities (FIXED fee: invoices; RETAINER: retainer agreement; HOURLY:
   *       no-op)
   * </ol>
   *
   * <p><b>Authorization:</b> No {@code @PreAuthorize} here — authorization is enforced at the
   * controller/portal layer. Epic 234A will add {@code PortalProposalController} with portal auth.
   *
   * @param proposalId the proposal to accept
   * @param portalContactId the portal contact who accepted the proposal
   * @return orchestration result with references to all created entities
   */
  @Transactional
  public OrchestrationResult acceptProposal(UUID proposalId, UUID portalContactId) {
    // Load proposal first (before try-catch) so we have context for failure events
    var proposal =
        proposalRepository
            .findById(proposalId)
            .orElseThrow(() -> new ResourceNotFoundException("Proposal", proposalId));

    // Validation checks — these should throw before we start orchestration
    if (proposal.getStatus() != ProposalStatus.SENT) {
      throw new InvalidStateException(
          "Invalid proposal state", "Cannot accept proposal in status " + proposal.getStatus());
    }

    if (!proposal.getPortalContactId().equals(portalContactId)) {
      throw new InvalidStateException(
          "Portal contact mismatch",
          "Portal contact does not match the proposal's assigned contact");
    }

    try {
      // Step 1: Update proposal status
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

      // Step 5: Create billing entities
      UUID retainerAgreementId = null;
      var createdInvoiceIds = List.<UUID>of();

      if (proposal.getFeeModel() == FeeModel.RETAINER) {
        retainerAgreementId = createRetainerAgreement(proposal);
      } else if (proposal.getFeeModel() == FeeModel.FIXED) {
        createdInvoiceIds = createFixedFeeInvoices(proposal, project.getId());
      }
      // HOURLY — no billing entities

      log.info(
          "Orchestration complete for proposal {}: project={}, members={}, invoices={}, retainer={}",
          proposalId,
          project.getId(),
          assignedMemberIds.size(),
          createdInvoiceIds.size(),
          retainerAgreementId);

      // Publish success event (fires AFTER_COMMIT)
      var customer = customerRepository.findById(proposal.getCustomerId()).orElseThrow();

      // Step 3a: Check engagement prerequisites and notify if not met (after customer load)
      if (proposal.getProjectTemplateId() != null) {
        var prereqCheck =
            prerequisiteService.checkEngagementPrerequisites(
                proposal.getCustomerId(), proposal.getProjectTemplateId());
        if (!prereqCheck.passed()) {
          var template = templateRepository.findById(proposal.getProjectTemplateId()).orElseThrow();
          String fieldList =
              prereqCheck.violations().stream()
                  .map(v -> v.fieldSlug())
                  .collect(Collectors.joining(", "));
          String notifTitle =
              "Customer %s is missing fields required for %s: %s"
                  .formatted(customer.getName(), template.getName(), fieldList);
          try {
            notificationService.notifyAdminsAndOwners(
                "PREREQUISITE_BLOCKED_ACTIVATION", notifTitle, null, "PROJECT", project.getId());
          } catch (Exception e) {
            log.warn("Failed to send prerequisite notification: {}", e.getMessage());
          }
        }
      }

      eventPublisher.publishEvent(
          new ProposalAcceptedEvent(
              proposalId,
              project.getId(),
              proposal.getProposalNumber(),
              customer.getName(),
              project.getName(),
              assignedMemberIds,
              proposal.getCreatedById(),
              RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null,
              RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null));

      return new OrchestrationResult(
          proposalId, project.getId(), assignedMemberIds, createdInvoiceIds, retainerAgreementId);
    } catch (RuntimeException e) {
      log.error("Orchestration failed for proposal {}: {}", proposalId, e.getMessage(), e);

      // Publish failure event (fires AFTER_ROLLBACK)
      eventPublisher.publishEvent(
          new ProposalOrchestrationFailedEvent(
              proposalId,
              proposal.getProposalNumber(),
              proposal.getCreatedById(),
              e.getMessage(),
              RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null,
              RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null));

      throw e;
    }
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

  // --- Step 5a: RETAINER billing setup ---

  private UUID createRetainerAgreement(Proposal proposal) {
    // Proposal v1 supports basic retainer config; advanced fields (type, frequency, rollover)
    // may be added to proposals in a future phase.
    var request =
        new CreateRetainerRequest(
            proposal.getCustomerId(),
            null, // scheduleId
            "Retainer — " + proposal.getTitle(),
            RetainerType.HOUR_BANK,
            RetainerFrequency.MONTHLY,
            LocalDate.now(),
            null, // endDate
            proposal.getRetainerHoursIncluded(),
            proposal.getRetainerAmount(),
            RolloverPolicy.FORFEIT,
            null, // rolloverCapHours
            null // notes
            );

    var retainerResponse =
        retainerAgreementService.createRetainer(request, proposal.getCreatedById());
    proposal.setCreatedRetainerId(retainerResponse.id());
    proposalRepository.save(proposal);

    log.info(
        "Created retainer agreement {} for proposal {}", retainerResponse.id(), proposal.getId());
    return retainerResponse.id();
  }

  // --- Step 5b: FIXED fee billing setup ---

  /*
   * Invoice creation bypasses InvoiceService.createDraft() intentionally (ADR-125).
   * At this point the customer is in ONBOARDING status (transitioned in step 2), but
   * InvoiceService routes through CustomerLifecycleGuard which requires ACTIVE status.
   * Since the entire orchestration runs in a single transaction, we create invoices
   * directly via the repository to avoid the guard check. The guard's purpose (blocking
   * work on PROSPECT customers) is satisfied — the customer is already past PROSPECT.
   */
  private List<UUID> createFixedFeeInvoices(Proposal proposal, UUID projectId) {
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
    line.setLineType(InvoiceLineType.FIXED_FEE);
    invoiceLineRepository.save(line);

    // Set subtotal/total on the invoice
    invoice.recalculateTotals(line.getAmount(), false, BigDecimal.ZERO, false);
    // Set due date
    invoice.updateDraft(dueDate, null, null, BigDecimal.ZERO);

    return invoiceRepository.save(invoice);
  }
}

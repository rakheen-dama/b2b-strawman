package io.b2mash.b2b.b2bstrawman.expense;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.ExpenseCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.ExpenseDeletedEvent;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.task.TaskRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

  private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

  private final ExpenseRepository expenseRepository;
  private final ProjectAccessService projectAccessService;
  private final ProjectLifecycleGuard projectLifecycleGuard;
  private final TaskRepository taskRepository;
  private final DocumentRepository documentRepository;
  private final OrgSettingsRepository orgSettingsRepository;
  private final AuditService auditService;
  private final ApplicationEventPublisher applicationEventPublisher;

  public ExpenseService(
      ExpenseRepository expenseRepository,
      ProjectAccessService projectAccessService,
      ProjectLifecycleGuard projectLifecycleGuard,
      TaskRepository taskRepository,
      DocumentRepository documentRepository,
      OrgSettingsRepository orgSettingsRepository,
      AuditService auditService,
      ApplicationEventPublisher applicationEventPublisher) {
    this.expenseRepository = expenseRepository;
    this.projectAccessService = projectAccessService;
    this.projectLifecycleGuard = projectLifecycleGuard;
    this.taskRepository = taskRepository;
    this.documentRepository = documentRepository;
    this.orgSettingsRepository = orgSettingsRepository;
    this.auditService = auditService;
    this.applicationEventPublisher = applicationEventPublisher;
  }

  @Transactional
  public Expense createExpense(
      UUID projectId,
      ActorContext actor,
      LocalDate date,
      String description,
      BigDecimal amount,
      String currency,
      ExpenseCategory category,
      UUID taskId,
      UUID receiptDocumentId,
      BigDecimal markupPercent,
      Boolean billable,
      String notes) {

    // Validate project is ACTIVE
    projectLifecycleGuard.requireActive(projectId);

    // Project access check
    projectAccessService.requireViewAccess(projectId, actor);

    // Task belongs to project (if provided)
    if (taskId != null) {
      var task =
          taskRepository
              .findById(taskId)
              .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
      if (!task.getProjectId().equals(projectId)) {
        throw new ResourceNotFoundException("Task", taskId);
      }
    }

    // Receipt document exists (if provided)
    if (receiptDocumentId != null) {
      documentRepository
          .findById(receiptDocumentId)
          .orElseThrow(() -> new ResourceNotFoundException("Document", receiptDocumentId));
    }

    // Validate amount > 0
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidStateException("Invalid amount", "Amount must be greater than 0");
    }

    // Validate markup if provided
    if (markupPercent != null && markupPercent.compareTo(BigDecimal.ZERO) < 0) {
      throw new InvalidStateException("Invalid markup", "Markup percent must be non-negative");
    }

    // Default currency from OrgSettings if not provided
    String effectiveCurrency = currency;
    if (effectiveCurrency == null || effectiveCurrency.isBlank()) {
      var orgSettings = orgSettingsRepository.findForCurrentTenant().orElse(null);
      effectiveCurrency = orgSettings != null ? orgSettings.getDefaultCurrency() : "ZAR";
    }

    // Create expense
    var expense =
        new Expense(
            projectId, actor.memberId(), date, description, amount, effectiveCurrency, category);
    if (taskId != null) expense.setTaskId(taskId);
    if (receiptDocumentId != null) expense.setReceiptDocumentId(receiptDocumentId);
    if (markupPercent != null) expense.setMarkupPercent(markupPercent);
    if (notes != null) expense.setNotes(notes);
    if (billable != null && !billable) {
      expense.writeOff();
    }

    var saved = expenseRepository.save(expense);
    log.info(
        "Created expense {} for project {} by member {}",
        saved.getId(),
        projectId,
        actor.memberId());

    // Audit
    var auditDetails = new LinkedHashMap<String, Object>();
    auditDetails.put("project_id", projectId.toString());
    auditDetails.put("amount", amount.toString());
    auditDetails.put("currency", effectiveCurrency);
    auditDetails.put("category", category.name());
    if (taskId != null) auditDetails.put("task_id", taskId.toString());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("expense.created")
            .entityType("expense")
            .entityId(saved.getId())
            .details(auditDetails)
            .build());

    // Publish domain event
    publishExpenseCreatedEvent(saved);

    return saved;
  }

  @Transactional(readOnly = true)
  public Expense getExpense(UUID projectId, UUID expenseId, ActorContext actor) {
    projectAccessService.requireViewAccess(projectId, actor);

    var expense =
        expenseRepository
            .findById(expenseId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));

    if (!expense.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Expense", expenseId);
    }

    return expense;
  }

  @Transactional(readOnly = true)
  public Page<Expense> listExpenses(
      UUID projectId,
      ExpenseCategory category,
      LocalDate from,
      LocalDate to,
      UUID filterMemberId,
      Pageable pageable,
      ActorContext actor) {

    projectAccessService.requireViewAccess(projectId, actor);

    return expenseRepository.findFiltered(projectId, category, from, to, filterMemberId, pageable);
  }

  @Transactional
  public Expense updateExpense(
      UUID projectId,
      UUID expenseId,
      ActorContext actor,
      LocalDate date,
      String description,
      BigDecimal amount,
      String currency,
      ExpenseCategory category,
      UUID taskId,
      UUID receiptDocumentId,
      BigDecimal markupPercent,
      Boolean billable,
      String notes) {

    // Validate project is ACTIVE
    projectLifecycleGuard.requireActive(projectId);

    var expense =
        expenseRepository
            .findById(expenseId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));

    validateExpenseUpdate(expense, projectId, actor);
    validateExpenseReferences(projectId, taskId, receiptDocumentId, amount, markupPercent);
    applyExpenseChanges(
        expense,
        date,
        description,
        amount,
        currency,
        category,
        taskId,
        receiptDocumentId,
        markupPercent,
        billable,
        notes);

    var saved = expenseRepository.save(expense);
    log.info("Updated expense {} by member {}", expenseId, actor.memberId());

    // Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("expense.updated")
            .entityType("expense")
            .entityId(saved.getId())
            .details(Map.of("project_id", projectId.toString()))
            .build());

    return saved;
  }

  @Transactional
  public void deleteExpense(UUID projectId, UUID expenseId, ActorContext actor) {
    // Validate project is ACTIVE
    projectLifecycleGuard.requireActive(projectId);

    var expense =
        expenseRepository
            .findById(expenseId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));

    if (!expense.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Expense", expenseId);
    }

    // Guard: not BILLED
    if (expense.getInvoiceId() != null) {
      throw new ResourceConflictException(
          "Expense is billed", "Expense is part of an invoice. Void the invoice to unlock.");
    }

    // Permission: creator OR ADMIN+
    requireEditPermission(expense, actor);

    expenseRepository.delete(expense);
    log.info("Deleted expense {} by member {}", expenseId, actor.memberId());

    // Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("expense.deleted")
            .entityType("expense")
            .entityId(expense.getId())
            .details(
                Map.of(
                    "project_id", projectId.toString(),
                    "amount", expense.getAmount().toString(),
                    "category", expense.getCategory().name()))
            .build());

    // Publish domain event
    publishExpenseDeletedEvent(expense);
  }

  @Transactional
  public Expense writeOffExpense(UUID projectId, UUID expenseId, ActorContext actor) {
    // Validate project is ACTIVE
    projectLifecycleGuard.requireActive(projectId);

    var expense =
        expenseRepository
            .findById(expenseId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));

    if (!expense.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Expense", expenseId);
    }

    // ADMIN+ only
    requireAdminPermission(actor.orgRole());

    // Guard: not BILLED (entity.writeOff() checks this too, but give better error)
    if (expense.getInvoiceId() != null) {
      throw new ResourceConflictException(
          "Expense is billed", "Cannot write off a billed expense. Void the invoice first.");
    }

    try {
      expense.writeOff();
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Cannot write off expense", e.getMessage());
    }

    var saved = expenseRepository.save(expense);
    log.info("Wrote off expense {} by member {}", expenseId, actor.memberId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("expense.written_off")
            .entityType("expense")
            .entityId(saved.getId())
            .details(Map.of("project_id", projectId.toString()))
            .build());

    return saved;
  }

  @Transactional
  public Expense restoreExpense(UUID projectId, UUID expenseId, ActorContext actor) {
    // Validate project is ACTIVE
    projectLifecycleGuard.requireActive(projectId);

    var expense =
        expenseRepository
            .findById(expenseId)
            .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));

    if (!expense.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Expense", expenseId);
    }

    // ADMIN+ only
    requireAdminPermission(actor.orgRole());

    try {
      expense.restore();
    } catch (IllegalStateException e) {
      throw new ResourceConflictException("Cannot restore expense", e.getMessage());
    }

    var saved = expenseRepository.save(expense);
    log.info("Restored expense {} by member {}", expenseId, actor.memberId());

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("expense.restored")
            .entityType("expense")
            .entityId(saved.getId())
            .details(Map.of("project_id", projectId.toString()))
            .build());

    return saved;
  }

  @Transactional(readOnly = true)
  public Page<Expense> getMyExpenses(UUID memberId, Pageable pageable) {
    return expenseRepository.findByMemberId(memberId, pageable);
  }

  /**
   * Loads OrgSettings for the current tenant and returns the default expense markup percent.
   * Returns null if OrgSettings not found or no default set.
   */
  @Transactional(readOnly = true)
  public BigDecimal getOrgDefaultMarkupPercent() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(OrgSettings::getDefaultExpenseMarkupPercent)
        .orElse(null);
  }

  /** Validates that an expense can be updated: belongs to project, not billed, has permission. */
  private void validateExpenseUpdate(Expense expense, UUID projectId, ActorContext actor) {
    if (!expense.getProjectId().equals(projectId)) {
      throw new ResourceNotFoundException("Expense", expense.getId());
    }

    // Guard: must not be BILLED
    if (expense.getInvoiceId() != null) {
      throw new ResourceConflictException(
          "Expense is billed", "Expense is part of an invoice. Void the invoice to unlock.");
    }

    // Permission: creator OR ADMIN+
    requireEditPermission(expense, actor);
  }

  /** Validates references (task, document) and value constraints (amount, markup). */
  private void validateExpenseReferences(
      UUID projectId,
      UUID taskId,
      UUID receiptDocumentId,
      BigDecimal amount,
      BigDecimal markupPercent) {
    // Validate task if provided
    if (taskId != null) {
      var task =
          taskRepository
              .findById(taskId)
              .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
      if (!task.getProjectId().equals(projectId)) {
        throw new ResourceNotFoundException("Task", taskId);
      }
    }

    // Validate receipt document if provided
    if (receiptDocumentId != null) {
      documentRepository
          .findById(receiptDocumentId)
          .orElseThrow(() -> new ResourceNotFoundException("Document", receiptDocumentId));
    }

    // Validate amount
    if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new InvalidStateException("Invalid amount", "Amount must be greater than 0");
    }

    // Validate markup
    if (markupPercent != null && markupPercent.compareTo(BigDecimal.ZERO) < 0) {
      throw new InvalidStateException("Invalid markup", "Markup percent must be non-negative");
    }
  }

  /** Applies field updates to an expense, null-coalescing optional fields. */
  private void applyExpenseChanges(
      Expense expense,
      LocalDate date,
      String description,
      BigDecimal amount,
      String currency,
      ExpenseCategory category,
      UUID taskId,
      UUID receiptDocumentId,
      BigDecimal markupPercent,
      Boolean billable,
      String notes) {
    expense.update(
        date != null ? date : expense.getDate(),
        description != null ? description : expense.getDescription(),
        amount != null ? amount : expense.getAmount(),
        currency != null ? currency : expense.getCurrency(),
        category != null ? category : expense.getCategory(),
        taskId != null ? taskId : expense.getTaskId(),
        receiptDocumentId != null ? receiptDocumentId : expense.getReceiptDocumentId(),
        markupPercent != null ? markupPercent : expense.getMarkupPercent(),
        billable != null ? billable : expense.isBillable(),
        notes != null ? notes : expense.getNotes());
  }

  private void requireEditPermission(Expense expense, ActorContext actor) {
    if (expense.getMemberId().equals(actor.memberId())) {
      return; // creator can always modify own expenses
    }
    var access = projectAccessService.checkAccess(expense.getProjectId(), actor);
    if (!access.canEdit()) {
      throw new ForbiddenException(
          "Cannot modify expense",
          "Only the creator or a project lead/admin/owner can modify this expense");
    }
  }

  private void requireAdminPermission(String orgRole) {
    if (Roles.ORG_ADMIN.equals(orgRole) || Roles.ORG_OWNER.equals(orgRole)) {
      return;
    }
    if (RequestScopes.hasCapability("FINANCIAL_VISIBILITY")) {
      return;
    }
    throw new ForbiddenException(
        "Insufficient permissions", "Only org admins and owners can perform this action");
  }

  private void publishExpenseCreatedEvent(Expense expense) {
    var memberId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    var tenantId = RequestScopes.getTenantIdOrNull();
    var orgId = RequestScopes.getOrgIdOrNull();
    applicationEventPublisher.publishEvent(
        new ExpenseCreatedEvent(
            "expense.created",
            "expense",
            expense.getId(),
            expense.getProjectId(),
            memberId,
            null,
            tenantId,
            orgId,
            Instant.now(),
            Map.of(
                "project_id", expense.getProjectId().toString(),
                "category", expense.getCategory().name())));
  }

  private void publishExpenseDeletedEvent(Expense expense) {
    var memberId = RequestScopes.MEMBER_ID.isBound() ? RequestScopes.MEMBER_ID.get() : null;
    var tenantId = RequestScopes.getTenantIdOrNull();
    var orgId = RequestScopes.getOrgIdOrNull();
    applicationEventPublisher.publishEvent(
        new ExpenseDeletedEvent(
            "expense.deleted",
            "expense",
            expense.getId(),
            expense.getProjectId(),
            memberId,
            null,
            tenantId,
            orgId,
            Instant.now(),
            Map.of("project_id", expense.getProjectId().toString())));
  }
}

package io.b2mash.b2b.b2bstrawman.expense;

import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExpenseController {

  private final ExpenseService expenseService;
  private final MemberNameResolver memberNameResolver;

  public ExpenseController(ExpenseService expenseService, MemberNameResolver memberNameResolver) {
    this.expenseService = expenseService;
    this.memberNameResolver = memberNameResolver;
  }

  @PostMapping("/api/matters/{matterId}/expenses")
  public ResponseEntity<ExpenseResponse> createExpense(
      @PathVariable UUID matterId,
      @Valid @RequestBody CreateExpenseRequest request,
      ActorContext actor) {
    var expense =
        expenseService.createExpense(
            matterId,
            actor,
            request.date(),
            request.description(),
            request.amount(),
            request.currency(),
            request.category(),
            request.taskId(),
            request.receiptDocumentId(),
            request.markupPercent(),
            request.billable(),
            request.notes());
    var names = resolveNames(List.of(expense));
    return ResponseEntity.created(
            URI.create("/api/matters/" + matterId + "/expenses/" + expense.getId()))
        .body(ExpenseResponse.from(expense, names));
  }

  @GetMapping("/api/matters/{matterId}/expenses")
  public ResponseEntity<Page<ExpenseResponse>> listExpenses(
      @PathVariable UUID matterId,
      @RequestParam(required = false) ExpenseCategory category,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) UUID memberId,
      Pageable pageable,
      ActorContext actor) {
    var page = expenseService.listExpenses(matterId, category, from, to, memberId, pageable, actor);
    var names = resolveNames(page.getContent());
    return ResponseEntity.ok(page.map(e -> ExpenseResponse.from(e, names)));
  }

  @GetMapping("/api/matters/{matterId}/expenses/{id}")
  public ResponseEntity<ExpenseResponse> getExpense(
      @PathVariable UUID matterId, @PathVariable UUID id, ActorContext actor) {
    var expense = expenseService.getExpense(matterId, id, actor);
    var names = resolveNames(List.of(expense));
    return ResponseEntity.ok(ExpenseResponse.from(expense, names));
  }

  @PutMapping("/api/matters/{matterId}/expenses/{id}")
  public ResponseEntity<ExpenseResponse> updateExpense(
      @PathVariable UUID matterId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateExpenseRequest request,
      ActorContext actor) {
    var expense =
        expenseService.updateExpense(
            matterId,
            id,
            actor,
            request.date(),
            request.description(),
            request.amount(),
            request.currency(),
            request.category(),
            request.taskId(),
            request.receiptDocumentId(),
            request.markupPercent(),
            request.billable(),
            request.notes());
    var names = resolveNames(List.of(expense));
    return ResponseEntity.ok(ExpenseResponse.from(expense, names));
  }

  @DeleteMapping("/api/matters/{matterId}/expenses/{id}")
  public ResponseEntity<Void> deleteExpense(
      @PathVariable UUID matterId, @PathVariable UUID id, ActorContext actor) {
    expenseService.deleteExpense(matterId, id, actor);
    return ResponseEntity.noContent().build();
  }

  @PatchMapping("/api/matters/{matterId}/expenses/{id}/write-off")
  @RequiresCapability("FINANCIAL_VISIBILITY")
  public ResponseEntity<ExpenseResponse> writeOffExpense(
      @PathVariable UUID matterId, @PathVariable UUID id, ActorContext actor) {
    var expense = expenseService.writeOffExpense(matterId, id, actor);
    var names = resolveNames(List.of(expense));
    return ResponseEntity.ok(ExpenseResponse.from(expense, names));
  }

  @PatchMapping("/api/matters/{matterId}/expenses/{id}/restore")
  @RequiresCapability("FINANCIAL_VISIBILITY")
  public ResponseEntity<ExpenseResponse> restoreExpense(
      @PathVariable UUID matterId, @PathVariable UUID id, ActorContext actor) {
    var expense = expenseService.restoreExpense(matterId, id, actor);
    var names = resolveNames(List.of(expense));
    return ResponseEntity.ok(ExpenseResponse.from(expense, names));
  }

  @GetMapping("/api/expenses/mine")
  public ResponseEntity<Page<ExpenseResponse>> getMyExpenses(Pageable pageable) {
    UUID memberId = RequestScopes.requireMemberId();
    var page = expenseService.getMyExpenses(memberId, pageable);
    var names = resolveNames(page.getContent());
    return ResponseEntity.ok(page.map(e -> ExpenseResponse.from(e, names)));
  }

  // --- Name resolution helper ---

  private Map<UUID, String> resolveNames(List<Expense> expenses) {
    var ids =
        expenses.stream().map(Expense::getMemberId).filter(Objects::nonNull).distinct().toList();
    return memberNameResolver.resolveNames(ids);
  }

  // --- DTOs ---

  public record CreateExpenseRequest(
      @NotNull(message = "date is required") LocalDate date,
      @NotBlank(message = "description is required")
          @Size(max = 500, message = "description must not exceed 500 characters")
          String description,
      @NotNull(message = "amount is required") @Positive(message = "amount must be positive")
          BigDecimal amount,
      String currency,
      @NotNull(message = "category is required") ExpenseCategory category,
      UUID taskId,
      UUID receiptDocumentId,
      BigDecimal markupPercent,
      Boolean billable,
      @Size(max = 1000, message = "notes must not exceed 1000 characters") String notes) {}

  public record UpdateExpenseRequest(
      LocalDate date,
      @Size(min = 1, max = 500, message = "description must be between 1 and 500 characters")
          String description,
      @Positive(message = "amount must be positive") BigDecimal amount,
      String currency,
      ExpenseCategory category,
      UUID taskId,
      UUID receiptDocumentId,
      BigDecimal markupPercent,
      Boolean billable,
      @Size(max = 1000, message = "notes must not exceed 1000 characters") String notes) {}

  public record ExpenseResponse(
      UUID id,
      UUID matterId,
      UUID taskId,
      UUID memberId,
      String memberName,
      LocalDate date,
      String description,
      BigDecimal amount,
      String currency,
      ExpenseCategory category,
      UUID receiptDocumentId,
      boolean billable,
      ExpenseBillingStatus billingStatus,
      UUID invoiceId,
      BigDecimal markupPercent,
      BigDecimal billableAmount,
      String notes,
      Instant createdAt,
      Instant updatedAt) {

    public static ExpenseResponse from(Expense expense, Map<UUID, String> memberNames) {
      return new ExpenseResponse(
          expense.getId(),
          expense.getProjectId(),
          expense.getTaskId(),
          expense.getMemberId(),
          memberNames.get(expense.getMemberId()),
          expense.getDate(),
          expense.getDescription(),
          expense.getAmount(),
          expense.getCurrency(),
          expense.getCategory(),
          expense.getReceiptDocumentId(),
          expense.isBillable(),
          expense.getBillingStatus(),
          expense.getInvoiceId(),
          expense.getMarkupPercent(),
          expense.getBillableAmount(),
          expense.getNotes(),
          expense.getCreatedAt(),
          expense.getUpdatedAt());
    }
  }
}

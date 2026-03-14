package io.b2mash.b2b.b2bstrawman.budget;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/budget")
public class ProjectBudgetController {

  private final ProjectBudgetService projectBudgetService;

  public ProjectBudgetController(ProjectBudgetService projectBudgetService) {
    this.projectBudgetService = projectBudgetService;
  }

  @GetMapping
  public ResponseEntity<BudgetStatusResponse> getBudget(@PathVariable UUID projectId) {
    var actor = ActorContext.fromRequestScopes();
    var status = projectBudgetService.getBudgetWithStatus(projectId, actor);
    return ResponseEntity.ok(BudgetStatusResponse.from(status));
  }

  @GetMapping("/status")
  public ResponseEntity<LightweightStatusResponse> getBudgetStatus(@PathVariable UUID projectId) {
    var actor = ActorContext.fromRequestScopes();
    var status = projectBudgetService.getBudgetStatusOnly(projectId, actor);
    return ResponseEntity.ok(LightweightStatusResponse.from(status));
  }

  @PutMapping
  @RequiresCapability("FINANCIAL_VISIBILITY")
  public ResponseEntity<BudgetStatusResponse> upsertBudget(
      @PathVariable UUID projectId, @Valid @RequestBody UpsertBudgetRequest request) {
    var actor = ActorContext.fromRequestScopes();
    var status =
        projectBudgetService.upsertBudget(
            projectId,
            request.budgetHours(),
            request.budgetAmount(),
            request.budgetCurrency(),
            request.alertThresholdPct(),
            request.notes(),
            actor);
    return ResponseEntity.ok(BudgetStatusResponse.from(status));
  }

  @DeleteMapping
  @RequiresCapability("FINANCIAL_VISIBILITY")
  public ResponseEntity<Void> deleteBudget(@PathVariable UUID projectId) {
    var actor = ActorContext.fromRequestScopes();
    projectBudgetService.deleteBudget(projectId, actor);
    return ResponseEntity.noContent().build();
  }

  // --- DTOs ---

  public record UpsertBudgetRequest(
      @Positive(message = "budgetHours must be positive") BigDecimal budgetHours,
      @Positive(message = "budgetAmount must be positive") BigDecimal budgetAmount,
      @Size(min = 3, max = 3, message = "budgetCurrency must be exactly 3 characters")
          String budgetCurrency,
      @Min(value = 50, message = "alertThresholdPct must be at least 50")
          @Max(value = 100, message = "alertThresholdPct must be at most 100")
          Integer alertThresholdPct,
      String notes) {}

  public record BudgetStatusResponse(
      UUID projectId,
      BigDecimal budgetHours,
      BigDecimal budgetAmount,
      String budgetCurrency,
      int alertThresholdPct,
      String notes,
      BigDecimal hoursConsumed,
      BigDecimal hoursRemaining,
      BigDecimal hoursConsumedPct,
      BigDecimal amountConsumed,
      BigDecimal amountRemaining,
      BigDecimal amountConsumedPct,
      String hoursStatus,
      String amountStatus,
      String overallStatus) {

    public static BudgetStatusResponse from(BudgetStatus s) {
      return new BudgetStatusResponse(
          s.projectId(),
          s.budgetHours(),
          s.budgetAmount(),
          s.budgetCurrency(),
          s.alertThresholdPct(),
          s.notes(),
          s.hoursConsumed(),
          s.hoursRemaining(),
          s.hoursConsumedPct(),
          s.amountConsumed(),
          s.amountRemaining(),
          s.amountConsumedPct(),
          s.hoursStatus() != null ? s.hoursStatus().name() : null,
          s.amountStatus() != null ? s.amountStatus().name() : null,
          s.overallStatus() != null ? s.overallStatus().name() : null);
    }
  }

  public record LightweightStatusResponse(
      BigDecimal hoursConsumedPct,
      BigDecimal amountConsumedPct,
      String hoursStatus,
      String amountStatus,
      String overallStatus) {

    public static LightweightStatusResponse from(BudgetStatus s) {
      return new LightweightStatusResponse(
          s.hoursConsumedPct(),
          s.amountConsumedPct(),
          s.hoursStatus() != null ? s.hoursStatus().name() : null,
          s.amountStatus() != null ? s.amountStatus().name() : null,
          s.overallStatus() != null ? s.overallStatus().name() : null);
    }
  }
}

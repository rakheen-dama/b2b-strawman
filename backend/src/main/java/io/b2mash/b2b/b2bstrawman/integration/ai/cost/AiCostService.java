package io.b2mash.b2b.b2bstrawman.integration.ai.cost;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiCompletionResponse;
import io.b2mash.b2b.b2bstrawman.integration.ai.execution.AiExecutionRepository;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfileService;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(AiPricingProperties.class)
public class AiCostService {

  private static final Logger log = LoggerFactory.getLogger(AiCostService.class);

  private final AiExecutionRepository executionRepository;
  private final AiFirmProfileService firmProfileService;
  private final AiPricingProperties pricingProperties;

  public AiCostService(
      AiExecutionRepository executionRepository,
      AiFirmProfileService firmProfileService,
      AiPricingProperties pricingProperties) {
    this.executionRepository = executionRepository;
    this.firmProfileService = firmProfileService;
    this.pricingProperties = pricingProperties;
  }

  /** Check if tenant has budget remaining. Throws if budget exhausted. */
  public void checkBudget(AiFirmProfile profile) {
    if (profile.getMonthlyBudgetCents() == null) {
      return; // no cap
    }
    Instant monthStart =
        YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    long spent = executionRepository.sumCostCentsForCurrentMonth(monthStart);
    if (spent >= profile.getMonthlyBudgetCents()) {
      throw new InvalidStateException(
          "AI budget exhausted",
          "Monthly AI spend of R%.2f has reached the budget of R%.2f"
              .formatted(spent / 100.0, profile.getMonthlyBudgetCents() / 100.0));
    }
  }

  /** Calculate cost in ZAR cents from token counts and model. */
  public long calculateCostCents(AiCompletionResponse response) {
    var pricing = pricingProperties.pricing().get(response.model());
    if (pricing == null) {
      // Fallback: use sonnet pricing if model not found
      pricing = pricingProperties.pricing().get("claude-sonnet-4-6");
    }
    if (pricing == null) {
      log.warn("No pricing config for model {} or fallback", response.model());
      return 0L;
    }
    double inputCostUsd =
        response.inputTokens() * pricing.inputPerMToken() / 1_000_000.0
            + response.cacheReadInputTokens() * pricing.cacheReadPerMToken() / 1_000_000.0
            + response.cacheCreationInputTokens() * pricing.cacheCreationPerMToken() / 1_000_000.0;
    double outputCostUsd = response.outputTokens() * pricing.outputPerMToken() / 1_000_000.0;
    double totalUsd = inputCostUsd + outputCostUsd;
    return Math.round(totalUsd * pricingProperties.exchangeRate().usdToZar() * 100);
  }

  /** Aggregate cost summary for the current month. */
  public AiCostSummary getCostSummary() {
    var profile = firmProfileService.getOrCreateProfile();
    Instant monthStart =
        YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    long spent = executionRepository.sumCostCentsForCurrentMonth(monthStart);
    int invocationCount = executionRepository.countForCurrentMonth(monthStart);
    Long budget = profile.getMonthlyBudgetCents();
    YearMonth period = YearMonth.now(ZoneOffset.UTC);
    return new AiCostSummary(
        spent,
        budget,
        invocationCount,
        budget != null ? budget - spent : null,
        period.atDay(1).toString(),
        period.atEndOfMonth().toString());
  }
}

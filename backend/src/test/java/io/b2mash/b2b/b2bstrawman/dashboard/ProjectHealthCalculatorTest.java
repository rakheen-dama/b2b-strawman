package io.b2mash.b2b.b2bstrawman.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectHealthCalculatorTest {

  @Test
  void noTasksReturnsUnknown() {
    var input = new ProjectHealthInput(0, 0, 0, null, 80, 0.0, 0);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.UNKNOWN);
    assertThat(result.reasons()).containsExactly("No tasks created yet");
  }

  @Test
  void allTasksDoneReturnsHealthy() {
    var input = new ProjectHealthInput(10, 10, 0, null, 80, 100.0, 1);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.HEALTHY);
    assertThat(result.reasons()).isEmpty();
  }

  @Test
  void highOverdueRatioReturnsCritical() {
    // 4 overdue of 10 total = 40% > 30%
    var input = new ProjectHealthInput(10, 3, 4, null, 80, 30.0, 1);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.CRITICAL);
    assertThat(result.reasons()).containsExactly("4 of 10 tasks overdue");
  }

  @Test
  void moderateOverdueRatioReturnsAtRisk() {
    // 2 overdue of 10 total = 20% > 10%
    var input = new ProjectHealthInput(10, 5, 2, null, 80, 50.0, 1);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.AT_RISK);
    assertThat(result.reasons()).containsExactly("2 overdue tasks");
  }

  @Test
  void budgetOverrunReturnsCritical() {
    var input = new ProjectHealthInput(10, 5, 0, 105.0, 80, 50.0, 1);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.CRITICAL);
    assertThat(result.reasons()).contains("Over budget");
  }

  @Test
  void budgetAtAlertThresholdWithLowCompletionReturnsAtRisk() {
    // 85% consumed, 50% complete, threshold 80 -> 50 < 85-10=75 -> AT_RISK
    var input = new ProjectHealthInput(10, 5, 0, 85.0, 80, 50.0, 1);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.AT_RISK);
    assertThat(result.reasons())
        .containsExactly("Budget 85% consumed but only 50% of tasks complete");
  }

  @Test
  void inactiveMoreThan14DaysReturnsAtRisk() {
    var input = new ProjectHealthInput(10, 5, 0, null, 80, 50.0, 21);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.AT_RISK);
    assertThat(result.reasons()).containsExactly("No activity in 21 days");
  }

  @Test
  void inactiveExactly14DaysReturnsHealthy() {
    // Boundary: > 14 not >= 14
    var input = new ProjectHealthInput(10, 10, 0, null, 80, 100.0, 14);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.HEALTHY);
    assertThat(result.reasons()).isEmpty();
  }

  @Test
  void nullBudgetSkipsBudgetRules() {
    var input = new ProjectHealthInput(10, 5, 0, null, 80, 50.0, 1);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.HEALTHY);
    assertThat(result.reasons()).isEmpty();
  }

  @Test
  void multipleRulesEscalateToWorst() {
    // Budget overrun (CRITICAL) + stale (AT_RISK) = CRITICAL with both reasons
    var input = new ProjectHealthInput(10, 5, 0, 110.0, 80, 50.0, 21);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.CRITICAL);
    assertThat(result.reasons()).contains("Over budget").contains("No activity in 21 days");
  }

  @Test
  void criticalAndAtRiskReasonsBothAppear() {
    // Budget overrun (CRITICAL) + budget at risk (AT_RISK, 110 >= 80 and 50 < 110-10=100)
    // + stale (AT_RISK) = CRITICAL with all reasons
    var input = new ProjectHealthInput(10, 5, 0, 110.0, 80, 50.0, 21);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.CRITICAL);
    assertThat(result.reasons()).hasSize(3);
    assertThat(result.reasons()).contains("Over budget");
    assertThat(result.reasons()).contains("Budget 110% consumed but only 50% of tasks complete");
    assertThat(result.reasons()).contains("No activity in 21 days");
  }

  @Test
  void zeroOverdueReturnsHealthy() {
    var input = new ProjectHealthInput(10, 5, 0, null, 80, 50.0, 1);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.HEALTHY);
    assertThat(result.reasons()).isEmpty();
  }

  @Test
  void budgetBelowAlertThresholdReturnsHealthy() {
    // 60% consumed, threshold 80 -> not at risk
    var input = new ProjectHealthInput(10, 5, 0, 60.0, 80, 50.0, 1);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.HEALTHY);
    assertThat(result.reasons()).isEmpty();
  }

  @Test
  void fullCompletionWithHighBudgetReturnsHealthy() {
    // 95% budget consumed, 100% completion, threshold 80
    // Rule 2 condition: 100 < 95-10=85 is false -> rule does not fire
    var input = new ProjectHealthInput(10, 10, 0, 95.0, 80, 100.0, 1);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.HEALTHY);
    assertThat(result.reasons()).isEmpty();
  }

  @Test
  void edgeCaseOneOfThreeOverdueReturnsCritical() {
    // 1 overdue of 3 total = 33.3% > 30% -> CRITICAL
    var input = new ProjectHealthInput(3, 1, 1, null, 80, 33.3, 1);
    var result = ProjectHealthCalculator.calculate(input);
    assertThat(result.status()).isEqualTo(HealthStatus.CRITICAL);
    assertThat(result.reasons()).containsExactly("1 of 3 tasks overdue");
  }

  @Test
  void severityOrderingIsCorrect() {
    assertThat(HealthStatus.UNKNOWN.severity()).isEqualTo(0);
    assertThat(HealthStatus.HEALTHY.severity()).isEqualTo(1);
    assertThat(HealthStatus.AT_RISK.severity()).isEqualTo(2);
    assertThat(HealthStatus.CRITICAL.severity()).isEqualTo(3);
  }
}

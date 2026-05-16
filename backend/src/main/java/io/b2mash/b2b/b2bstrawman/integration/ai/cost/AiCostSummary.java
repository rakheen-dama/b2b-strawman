package io.b2mash.b2b.b2bstrawman.integration.ai.cost;

public record AiCostSummary(
    long currentMonthSpentCents,
    Long monthlyBudgetCents,
    int invocationCount,
    Long remainingBudgetCents,
    String periodStart,
    String periodEnd) {}

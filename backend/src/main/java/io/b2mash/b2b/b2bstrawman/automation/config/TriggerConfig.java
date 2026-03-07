package io.b2mash.b2b.b2bstrawman.automation.config;

public sealed interface TriggerConfig
    permits StatusChangeTriggerConfig, BudgetThresholdTriggerConfig, EmptyTriggerConfig {}

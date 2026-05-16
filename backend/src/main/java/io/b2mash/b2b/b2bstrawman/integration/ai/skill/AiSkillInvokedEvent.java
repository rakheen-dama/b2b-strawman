package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import java.util.UUID;

public record AiSkillInvokedEvent(
    UUID executionId,
    String skillId,
    String entityType,
    UUID entityId,
    String model,
    int inputTokens,
    int outputTokens,
    long costCents,
    String status) {}

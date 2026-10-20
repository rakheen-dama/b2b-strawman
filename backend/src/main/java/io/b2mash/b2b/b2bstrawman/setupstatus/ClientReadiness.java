package io.b2mash.b2b.b2bstrawman.setupstatus;

import java.util.UUID;

public record ClientReadiness(
    UUID clientId,
    String lifecycleStatus,
    ChecklistProgress checklistProgress,
    RequiredFieldStatus requiredFields,
    boolean hasLinkedMatters,
    String overallReadiness) {}

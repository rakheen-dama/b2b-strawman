package io.b2mash.b2b.b2bstrawman.setupstatus;

import java.util.UUID;

public record CustomerReadiness(
    UUID customerId,
    String lifecycleStatus,
    ChecklistProgress checklistProgress,
    RequiredFieldStatus requiredFields,
    boolean hasLinkedProjects,
    String overallReadiness) {}

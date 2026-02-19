package io.b2mash.b2b.b2bstrawman.setupstatus;

import java.util.UUID;

public record ProjectSetupStatus(
    UUID projectId,
    boolean customerAssigned,
    boolean rateCardConfigured,
    boolean budgetConfigured,
    boolean teamAssigned,
    RequiredFieldStatus requiredFields,
    int completionPercentage,
    boolean overallComplete) {}

package io.b2mash.b2b.b2bstrawman.integration.ai.profile;

import java.util.List;
import java.util.Map;

public record UpdateAiFirmProfileRequest(
    List<String> practiceAreas,
    String jurisdiction,
    String riskCalibration,
    String houseStyleNotes,
    Map<String, Object> ficaRequirements,
    String feeEstimationNotes,
    String preferredModel,
    Long monthlyBudgetCents,
    Boolean coldStartCompleted) {}

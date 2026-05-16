package io.b2mash.b2b.b2bstrawman.integration.ai.profile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AiFirmProfileResponse(
    UUID id,
    List<String> practiceAreas,
    String jurisdiction,
    String riskCalibration,
    String houseStyleNotes,
    Map<String, Object> ficaRequirements,
    String feeEstimationNotes,
    String preferredModel,
    Long monthlyBudgetCents,
    int profileVersion,
    boolean coldStartCompleted,
    Instant createdAt,
    Instant updatedAt) {

  public static AiFirmProfileResponse from(AiFirmProfile profile) {
    return new AiFirmProfileResponse(
        profile.getId(),
        profile.getPracticeAreas(),
        profile.getJurisdiction(),
        profile.getRiskCalibration(),
        profile.getHouseStyleNotes(),
        profile.getFicaRequirements(),
        profile.getFeeEstimationNotes(),
        profile.getPreferredModel(),
        profile.getMonthlyBudgetCents(),
        profile.getProfileVersion(),
        profile.isColdStartCompleted(),
        profile.getCreatedAt(),
        profile.getUpdatedAt());
  }
}

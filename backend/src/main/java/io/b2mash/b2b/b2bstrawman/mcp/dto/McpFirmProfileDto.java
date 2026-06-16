package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.integration.ai.profile.AiFirmProfile;
import java.util.List;

/**
 * Firm AI grounding profile projection for the {@code kazi://firm-profile} resource (Epic 564B).
 * Deliberately projects ONLY the five client-safe grounding fields and EXCLUDES operational/secret
 * fields ({@code preferredModel}, {@code monthlyBudgetCents}, {@code ficaRequirements}, {@code
 * profileVersion}, {@code coldStartCompleted}, audit columns) — the resource is gated on {@code
 * AI_MANAGE} and must never leak budget/model configuration to the MCP client.
 *
 * @param practiceAreas the firm's practice areas
 * @param jurisdiction primary jurisdiction (e.g. "ZA")
 * @param riskCalibration risk posture (e.g. "CONSERVATIVE")
 * @param houseStyleNotes house-style grounding notes
 * @param feeEstimationNotes fee-estimation grounding notes
 */
public record McpFirmProfileDto(
    List<String> practiceAreas,
    String jurisdiction,
    String riskCalibration,
    String houseStyleNotes,
    String feeEstimationNotes) {

  /** Projects the entity, copying only the five allowed fields. */
  public static McpFirmProfileDto from(AiFirmProfile p) {
    return new McpFirmProfileDto(
        p.getPracticeAreas() == null ? List.of() : List.copyOf(p.getPracticeAreas()),
        p.getJurisdiction(),
        p.getRiskCalibration(),
        p.getHouseStyleNotes(),
        p.getFeeEstimationNotes());
  }
}

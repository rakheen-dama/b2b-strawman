package io.b2mash.b2b.b2bstrawman.integration.ai.profile;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiFirmProfileService {

  private final AiFirmProfileRepository repository;
  private final ApplicationEventPublisher eventPublisher;

  public AiFirmProfileService(
      AiFirmProfileRepository repository, ApplicationEventPublisher eventPublisher) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public AiFirmProfile getOrCreateProfile() {
    List<AiFirmProfile> profiles = repository.findAll();
    if (!profiles.isEmpty()) {
      return profiles.getFirst();
    }
    UUID memberId = RequestScopes.requireMemberId();
    AiFirmProfile profile = new AiFirmProfile(memberId);
    return repository.save(profile);
  }

  @Transactional
  public AiFirmProfile updateProfile(UpdateAiFirmProfileRequest request) {
    UUID memberId = RequestScopes.requireMemberId();
    List<AiFirmProfile> profiles = repository.findAll();
    AiFirmProfile profile;
    if (profiles.isEmpty()) {
      profile = new AiFirmProfile(memberId);
      profile = repository.save(profile);
    } else {
      profile = profiles.getFirst();
    }
    profile.updateProfile(
        request.practiceAreas(),
        request.jurisdiction(),
        request.riskCalibration(),
        request.houseStyleNotes(),
        request.ficaRequirements(),
        request.feeEstimationNotes(),
        request.preferredModel(),
        request.monthlyBudgetCents(),
        request.coldStartCompleted(),
        memberId);
    profile = repository.save(profile);
    eventPublisher.publishEvent(AiFirmProfileUpdatedEvent.of(profile));
    return profile;
  }

  @Transactional(readOnly = true)
  public String assembleProfileBlock() {
    List<AiFirmProfile> profiles = repository.findAll();
    if (profiles.isEmpty()) {
      return buildDefaultProfileBlock();
    }
    AiFirmProfile profile = profiles.getFirst();
    if (!profile.isColdStartCompleted()) {
      return buildDefaultProfileBlock();
    }
    return buildProfileBlock(profile);
  }

  private String buildDefaultProfileBlock() {
    return """
        <firm-profile version="0">
        Jurisdiction: ZA
        Risk calibration: CONSERVATIVE
        </firm-profile>""";
  }

  private String buildProfileBlock(AiFirmProfile profile) {
    var sb = new StringBuilder();
    sb.append("<firm-profile version=\"").append(profile.getProfileVersion()).append("\">\n");

    if (profile.getPracticeAreas() != null && !profile.getPracticeAreas().isEmpty()) {
      sb.append("Practice areas: ")
          .append(String.join(", ", profile.getPracticeAreas()))
          .append("\n");
    }

    sb.append("Jurisdiction: ").append(profile.getJurisdiction()).append("\n");
    sb.append("Risk calibration: ").append(profile.getRiskCalibration()).append("\n");

    if (profile.getHouseStyleNotes() != null && !profile.getHouseStyleNotes().isBlank()) {
      sb.append("House style: ").append(profile.getHouseStyleNotes()).append("\n");
    }

    if (profile.getFicaRequirements() != null && !profile.getFicaRequirements().isEmpty()) {
      String ficaStr =
          profile.getFicaRequirements().entrySet().stream()
              .map(e -> e.getKey() + "=" + e.getValue())
              .collect(Collectors.joining("; "));
      sb.append("FICA requirements: ").append(ficaStr).append("\n");
    }

    if (profile.getFeeEstimationNotes() != null && !profile.getFeeEstimationNotes().isBlank()) {
      sb.append("Fee estimation: ").append(profile.getFeeEstimationNotes()).append("\n");
    }

    sb.append("</firm-profile>");
    return sb.toString();
  }
}

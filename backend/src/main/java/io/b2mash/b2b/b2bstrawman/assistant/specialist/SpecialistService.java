package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-side facade for {@link SpecialistRegistry} that handles caller-context resolution + DTO
 * mapping. Keeps {@link SpecialistController} a one-line delegate per backend controller
 * discipline.
 */
@Service
public class SpecialistService {

  private final SpecialistRegistry specialistRegistry;

  public SpecialistService(SpecialistRegistry specialistRegistry) {
    this.specialistRegistry = specialistRegistry;
  }

  public List<SpecialistSummaryDto> listVisible(String route) {
    return specialistRegistry.visibleTo(RequestScopes.getCapabilities(), route).stream()
        .map(SpecialistService::toSummary)
        .toList();
  }

  public SpecialistDetailDto detail(String id) {
    var s = specialistRegistry.requireById(id);
    return new SpecialistDetailDto(
        s.id(),
        s.displayName(),
        s.tagline(),
        s.toolIds(),
        s.launchers(),
        s.automationCapable(),
        s.maxToolIterations());
  }

  private static SpecialistSummaryDto toSummary(Specialist s) {
    return new SpecialistSummaryDto(s.id(), s.displayName(), s.tagline(), s.launchers());
  }

  public record SpecialistSummaryDto(
      String id, String displayName, String tagline, List<LauncherContext> launchers) {}

  public record SpecialistDetailDto(
      String id,
      String displayName,
      String tagline,
      List<String> toolIds,
      List<LauncherContext> launchers,
      boolean automationCapable,
      int maxToolIterations) {}
}

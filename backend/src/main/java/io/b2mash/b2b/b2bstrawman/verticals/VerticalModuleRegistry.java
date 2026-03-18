package io.b2mash.b2b.b2bstrawman.verticals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Static registry of known vertical modules. Modules are stub placeholders until implemented. */
@Component
public class VerticalModuleRegistry {

  /** Immutable definition of a vertical module. */
  public record ModuleDefinition(String id, String name, String description, String status) {}

  private final Map<String, ModuleDefinition> modules;

  public VerticalModuleRegistry() {
    this.modules =
        Map.of(
            "trust_accounting",
            new ModuleDefinition(
                "trust_accounting",
                "Trust Accounting",
                "LSSA-compliant trust account management for client funds",
                "stub"),
            "court_calendar",
            new ModuleDefinition(
                "court_calendar",
                "Court Calendar",
                "Court date tracking and deadline management",
                "stub"),
            "conflict_check",
            new ModuleDefinition(
                "conflict_check", "Conflict Check", "Matter conflict of interest checks", "stub"));
  }

  /** Returns all known module definitions. */
  public List<ModuleDefinition> getAllModules() {
    return List.copyOf(modules.values());
  }

  /** Returns the module definition for the given ID, or empty if not found. */
  public Optional<ModuleDefinition> getModule(String id) {
    return Optional.ofNullable(modules.get(id));
  }
}

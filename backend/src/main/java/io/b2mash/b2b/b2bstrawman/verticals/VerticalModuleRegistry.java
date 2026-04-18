package io.b2mash.b2b.b2bstrawman.verticals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Static registry of known modules. Modules are stub placeholders until implemented.
 *
 * <p>Each module is tagged with a {@link ModuleCategory}:
 *
 * <ul>
 *   <li>{@link ModuleCategory#VERTICAL} modules are auto-assigned by the vertical profile and are
 *       not shown in Settings → Features.
 *   <li>{@link ModuleCategory#HORIZONTAL} modules are manually toggled by org admins in Settings →
 *       Features and are independent of the selected vertical profile.
 * </ul>
 *
 * <p>See ADR-239 (Horizontal vs. Vertical Module Gating).
 */
@Component
public class VerticalModuleRegistry {

  /** Navigation item for a module. */
  public record NavItem(String path, String label, String zone) {}

  /** Immutable definition of a module. */
  public record ModuleDefinition(
      String id,
      String name,
      String description,
      String status,
      ModuleCategory category,
      List<String> defaultEnabledFor,
      List<NavItem> navItems) {}

  private final Map<String, ModuleDefinition> modules;

  public VerticalModuleRegistry() {
    // Use LinkedHashMap to preserve insertion order (Map.of has a 10-entry overload limit).
    var map = new LinkedHashMap<String, ModuleDefinition>();

    map.put(
        "trust_accounting",
        new ModuleDefinition(
            "trust_accounting",
            "Trust Accounting",
            "LSSA-compliant trust account management for client funds",
            "active",
            ModuleCategory.VERTICAL,
            List.of("legal-za"),
            List.of(
                new NavItem("/trust-accounting", "Trust Accounting", "legal"),
                new NavItem("/trust-accounting/transactions", "Transactions", "legal"),
                new NavItem("/trust-accounting/client-ledgers", "Client Ledgers", "legal"),
                new NavItem("/trust-accounting/reconciliation", "Reconciliation", "legal"),
                new NavItem("/trust-accounting/interest", "Interest", "legal"),
                new NavItem("/trust-accounting/investments", "Investments", "legal"),
                new NavItem("/trust-accounting/reports", "Trust Reports", "legal"))));

    map.put(
        "court_calendar",
        new ModuleDefinition(
            "court_calendar",
            "Court Calendar",
            "Court date tracking and deadline management",
            "active",
            ModuleCategory.VERTICAL,
            List.of("legal-za"),
            List.of(new NavItem("/court-calendar", "Court Calendar", "legal"))));

    map.put(
        "conflict_check",
        new ModuleDefinition(
            "conflict_check",
            "Conflict Check",
            "Matter conflict of interest checks",
            "active",
            ModuleCategory.VERTICAL,
            List.of("legal-za"),
            List.of(new NavItem("/conflict-check", "Conflict Check", "legal"))));

    map.put(
        "regulatory_deadlines",
        new ModuleDefinition(
            "regulatory_deadlines",
            "Regulatory Deadlines",
            "Firm-wide calendar of regulatory filing deadlines with status tracking",
            "active",
            ModuleCategory.VERTICAL,
            List.of("accounting-za"),
            List.of(new NavItem("/deadlines", "Deadlines", "clients"))));

    map.put(
        "lssa_tariff",
        new ModuleDefinition(
            "lssa_tariff",
            "LSSA Tariff",
            "LSSA tariff schedule management for legal billing",
            "active",
            ModuleCategory.VERTICAL,
            List.of("legal-za"),
            List.of(new NavItem("/legal/tariffs", "Tariffs", "finance"))));

    // --- Horizontal modules (profile-independent, manually toggled) ---

    map.put(
        "resource_planning",
        new ModuleDefinition(
            "resource_planning",
            "Resource Planning",
            "Team allocation grid, capacity forecasting, and utilization tracking. Best for firms"
                + " with 10+ team members managing multiple concurrent projects.",
            "active",
            ModuleCategory.HORIZONTAL,
            List.of(),
            List.of(
                new NavItem("/resources", "Resources", "work"),
                new NavItem("/resources/utilization", "Utilization", "work"))));

    map.put(
        "bulk_billing",
        new ModuleDefinition(
            "bulk_billing",
            "Bulk Billing Runs",
            "Batch invoice generation across multiple customers in a single run. Best for firms"
                + " billing 10+ clients per cycle.",
            "active",
            ModuleCategory.HORIZONTAL,
            List.of(),
            List.of(new NavItem("/invoices/billing-runs", "Billing Runs", "finance"))));

    map.put(
        "automation_builder",
        new ModuleDefinition(
            "automation_builder",
            "Automation Rule Builder",
            "Create custom workflow automations with triggers, conditions, and actions. Standard"
                + " automations run automatically — enable this to customize or create new rules.",
            "active",
            ModuleCategory.HORIZONTAL,
            List.of(),
            List.of(new NavItem("/settings/automations", "Automations", "work"))));

    map.put(
        "disbursements",
        new ModuleDefinition(
            "disbursements",
            "Disbursements",
            "Out-of-pocket costs incurred on behalf of clients (sheriff fees, deeds office,"
                + " counsel)",
            "active",
            ModuleCategory.VERTICAL,
            List.of("legal-za"),
            List.of(new NavItem("/legal/disbursements", "Disbursements", "legal"))));

    this.modules = Map.copyOf(map);
  }

  /** Returns all known module definitions. */
  public List<ModuleDefinition> getAllModules() {
    return List.copyOf(modules.values());
  }

  /** Returns the module definition for the given ID, or empty if not found. */
  public Optional<ModuleDefinition> getModule(String id) {
    return Optional.ofNullable(modules.get(id));
  }

  /**
   * Returns all module definitions with {@link ModuleCategory#HORIZONTAL}. Horizontal modules are
   * manually toggled by org admins and surface in Settings → Features.
   */
  public List<ModuleDefinition> getHorizontalModules() {
    return getModulesByCategory(ModuleCategory.HORIZONTAL);
  }

  /** Returns all module definitions matching the given category. */
  public List<ModuleDefinition> getModulesByCategory(ModuleCategory category) {
    return modules.values().stream().filter(m -> m.category() == category).toList();
  }
}

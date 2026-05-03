package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import io.b2mash.b2b.b2bstrawman.billing.PlanTier;
import io.b2mash.b2b.b2bstrawman.member.Member;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * In-code catalogue of AI specialists. Per ADR-265 the catalogue is a {@code @Component} with no
 * interface — there is one production implementation and YAGNI applies. Pattern follows {@link
 * io.b2mash.b2b.b2bstrawman.audit.AuditEventTypeRegistry}.
 *
 * <p>The 511A {@link #visibleTo} implementation is intentionally narrow: it gates on PRO tier and
 * the {@code AI_ASSISTANT_USE} capability override. 511B replaces the capability check with {@code
 * CapabilityAuthorizationService} integration.
 */
@Component
public class SpecialistRegistry {

  /** Capability override required for any specialist to be visible. */
  public static final String CAPABILITY_AI_ASSISTANT_USE = "AI_ASSISTANT_USE";

  private static final int DEFAULT_MAX_TOOL_ITERATIONS = 8;

  private final Map<String, Specialist> byId;
  private final List<Specialist> all;

  public SpecialistRegistry() {
    var specialists =
        List.of(
            new Specialist(
                "BILLING",
                "assistant.specialist.billing.displayName",
                "assistant.specialist.billing.tagline",
                "assistant/specialists/billing-za.md",
                List.of(
                    "GetInvoice",
                    "GetUnbilledTime",
                    "GetTimeSummary",
                    "ListProjects",
                    "GetProject",
                    "ListCustomers",
                    "GetCustomer",
                    "ProposeTimeEntryPolish",
                    "ProposeInvoiceLineGrouping"),
                List.of(
                    new LauncherContext(
                        "/invoices/:id",
                        "INVOICE_DRAFT_TOOLBAR",
                        "assistant.launcher.cta.INVOICE_DRAFT_TOOLBAR"),
                    new LauncherContext(
                        "/time/unbilled",
                        "UNBILLED_TIME_DIALOG",
                        "assistant.launcher.cta.UNBILLED_TIME_DIALOG")),
                false,
                DEFAULT_MAX_TOOL_ITERATIONS),
            new Specialist(
                "INTAKE",
                "assistant.specialist.intake.displayName",
                "assistant.specialist.intake.tagline",
                "assistant/specialists/intake-za.md",
                List.of(
                    "ListDocumentsForContext",
                    "ExtractTextFromDocument",
                    "GetCustomer",
                    "ProposeCustomerFieldExtraction"),
                List.of(
                    new LauncherContext(
                        "/customers/new",
                        "CUSTOMER_CREATE_DIALOG",
                        "assistant.launcher.cta.CUSTOMER_CREATE_DIALOG"),
                    new LauncherContext(
                        "/requests/:id",
                        "INFO_REQUEST_REVIEW",
                        "assistant.launcher.cta.INFO_REQUEST_REVIEW"),
                    new LauncherContext(
                        "/customers/:id",
                        "CUSTOMER_DETAIL_PREREQ",
                        "assistant.launcher.cta.CUSTOMER_DETAIL_PREREQ")),
                false,
                DEFAULT_MAX_TOOL_ITERATIONS),
            new Specialist(
                "INBOX",
                "assistant.specialist.inbox.displayName",
                "assistant.specialist.inbox.tagline",
                "assistant/specialists/inbox-za.md",
                List.of("GetMatterActivityWindow", "GetCustomer", "GetProject", "PostInboxSummary"),
                List.of(
                    new LauncherContext(
                        "/projects/:id",
                        "MATTER_ACTIVITY_TAB",
                        "assistant.launcher.cta.MATTER_ACTIVITY_TAB"),
                    new LauncherContext(
                        "/customers/:id",
                        "CUSTOMER_DETAIL",
                        "assistant.launcher.cta.CUSTOMER_DETAIL")),
                true,
                DEFAULT_MAX_TOOL_ITERATIONS));

    var map = new HashMap<String, Specialist>();
    for (var specialist : specialists) {
      var previous = map.put(specialist.id(), specialist);
      if (previous != null) {
        throw new IllegalStateException("Duplicate specialist id in registry: " + specialist.id());
      }
    }
    this.byId = Collections.unmodifiableMap(map);
    this.all = List.copyOf(specialists);
  }

  /**
   * Look up a specialist by its stable id.
   *
   * @throws IllegalArgumentException if no specialist is registered with the given id
   */
  public Specialist findById(String id) {
    var specialist = byId.get(id);
    if (specialist == null) {
      throw new IllegalArgumentException("No specialist registered with id: \"" + id + "\"");
    }
    return specialist;
  }

  /** Returns every registered specialist, in insertion order. */
  public List<Specialist> all() {
    return all;
  }

  /**
   * Filters the catalogue down to specialists visible to {@code member} on a given {@code surface}
   * under a given plan {@code tier}.
   *
   * <p>511A scope: PRO-only, capability-gated via {@code AI_ASSISTANT_USE} on the member's
   * capability overrides. 511B replaces the capability check with {@code
   * CapabilityAuthorizationService}.
   *
   * @param member the calling member (capability source)
   * @param tier the tenant's current plan tier
   * @param surface the launcher surface key (e.g. {@code "INVOICE_DRAFT_TOOLBAR"}); {@code null} or
   *     blank means "no surface filter"
   */
  public List<Specialist> visibleTo(Member member, PlanTier tier, String surface) {
    if (tier != PlanTier.PRO) {
      return List.of();
    }
    var caps = member.getCapabilityOverrides();
    if (caps == null || !caps.contains(CAPABILITY_AI_ASSISTANT_USE)) {
      return List.of();
    }
    return filterBySurface(surface);
  }

  /**
   * Variant of {@link #visibleTo(Member, PlanTier, String)} that consults a pre-resolved capability
   * set (typically {@code RequestScopes.getCapabilities()}). Used by 511B's HTTP layer where the
   * effective capabilities have already been computed by {@code MemberFilter}.
   */
  public List<Specialist> visibleToCapabilities(
      Set<String> capabilities, PlanTier tier, String surface) {
    if (tier != PlanTier.PRO) {
      return List.of();
    }
    if (capabilities == null || !capabilities.contains(CAPABILITY_AI_ASSISTANT_USE)) {
      return List.of();
    }
    return filterBySurface(surface);
  }

  private List<Specialist> filterBySurface(String surface) {
    return all.stream()
        .filter(
            s ->
                surface == null
                    || surface.isBlank()
                    || s.launchers().stream().anyMatch(l -> surface.equals(l.surface())))
        .toList();
  }
}

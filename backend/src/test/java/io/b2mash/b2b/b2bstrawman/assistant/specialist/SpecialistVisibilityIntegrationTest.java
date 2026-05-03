package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.billing.PlanTier;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Phase 70 / Epic 511B unit-level visibility tests covering the new {@link
 * SpecialistRegistry#visibleToCapabilities(Set, PlanTier, String)} overload that consults the
 * pre-resolved {@code RequestScopes.CAPABILITIES} set instead of the per-{@link
 * io.b2mash.b2b.b2bstrawman.member.Member} capability override list. No Spring context — this
 * registry is a self-contained component.
 */
class SpecialistVisibilityIntegrationTest {

  private final SpecialistRegistry registry = new SpecialistRegistry();

  @Test
  void capabilitiesWithAiAssistantUseAndProTierSeesAllSpecialists() {
    var caps = Set.of("AI_ASSISTANT_USE");
    var visible = registry.visibleToCapabilities(caps, PlanTier.PRO, null);
    assertThat(visible).extracting(Specialist::id).containsExactly("BILLING", "INTAKE", "INBOX");
  }

  @Test
  void starterTierAlwaysReturnsEmpty() {
    var caps = Set.of("AI_ASSISTANT_USE");
    assertThat(registry.visibleToCapabilities(caps, PlanTier.STARTER, null)).isEmpty();
  }

  @Test
  void capabilityMissingReturnsEmpty() {
    var caps = Set.of("INVOICING", "PROJECT_MANAGEMENT");
    assertThat(registry.visibleToCapabilities(caps, PlanTier.PRO, null)).isEmpty();
  }

  @Test
  void surfaceFilterNarrowsToBillingForInvoiceDraftToolbar() {
    var caps = Set.of("AI_ASSISTANT_USE");
    var visible = registry.visibleToCapabilities(caps, PlanTier.PRO, "INVOICE_DRAFT_TOOLBAR");
    assertThat(visible).extracting(Specialist::id).containsExactly("BILLING");
  }

  @Test
  void surfaceFilterReturnsEmptyWhenNoSpecialistMatches() {
    var caps = Set.of("AI_ASSISTANT_USE");
    var visible = registry.visibleToCapabilities(caps, PlanTier.PRO, "NONEXISTENT_SURFACE_KEY");
    assertThat(visible).isEmpty();
  }

  @Test
  void nullCapabilitySetTreatedAsEmpty() {
    assertThat(registry.visibleToCapabilities(null, PlanTier.PRO, null)).isEmpty();
  }
}

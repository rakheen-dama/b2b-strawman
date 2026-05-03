package io.b2mash.b2b.b2bstrawman.assistant.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.billing.PlanTier;
import io.b2mash.b2b.b2bstrawman.member.Member;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SpecialistRegistry}. No Spring context — instantiate the registry directly
 * because it is a self-contained pure-Java component.
 */
class SpecialistRegistryTest {

  private final SpecialistRegistry registry = new SpecialistRegistry();

  private static Member memberWithCapabilities(Set<String> caps) {
    var m = new Member("user_1", "user1@example.test", "User One", null, null);
    m.setCapabilityOverrides(caps);
    return m;
  }

  @Test
  void findByIdReturnsSpecialistAndUnknownIdThrows() {
    var billing = registry.findById("BILLING");
    assertThat(billing.id()).isEqualTo("BILLING");
    assertThat(billing.systemPromptResource()).isEqualTo("assistant/specialists/billing-za.md");
    assertThat(billing.automationCapable()).isFalse();
    assertThat(billing.maxToolIterations()).isEqualTo(8);

    var inbox = registry.findById("INBOX");
    assertThat(inbox.automationCapable())
        .as("INBOX is the sole DIRECT-capable specialist")
        .isTrue();

    assertThatThrownBy(() -> registry.findById("UNKNOWN"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UNKNOWN");
  }

  @Test
  void allReturnsThreeSpecialistsInRegistrationOrder() {
    assertThat(registry.all())
        .extracting(Specialist::id)
        .containsExactly("BILLING", "INTAKE", "INBOX");
  }

  @Test
  void visibleToFiltersByPlanTierCapabilityAndSurface() {
    var enabled = memberWithCapabilities(Set.of("AI_ASSISTANT_USE"));
    var withoutCap = memberWithCapabilities(Set.of());

    // STARTER tier — never visible regardless of capability.
    assertThat(registry.visibleTo(enabled, PlanTier.STARTER, null)).isEmpty();

    // PRO tier without the capability override — empty.
    assertThat(registry.visibleTo(withoutCap, PlanTier.PRO, null)).isEmpty();

    // PRO + capability + null surface — every specialist is visible.
    assertThat(registry.visibleTo(enabled, PlanTier.PRO, null))
        .extracting(Specialist::id)
        .containsExactly("BILLING", "INTAKE", "INBOX");

    // PRO + capability + surface filter — only specialists with a matching launcher.
    assertThat(registry.visibleTo(enabled, PlanTier.PRO, "INVOICE_DRAFT_TOOLBAR"))
        .extracting(Specialist::id)
        .containsExactly("BILLING");

    assertThat(registry.visibleTo(enabled, PlanTier.PRO, "MATTER_ACTIVITY_TAB"))
        .extracting(Specialist::id)
        .containsExactly("INBOX");

    // Unknown surface — empty.
    assertThat(registry.visibleTo(enabled, PlanTier.PRO, "DOES_NOT_EXIST")).isEmpty();
  }
}

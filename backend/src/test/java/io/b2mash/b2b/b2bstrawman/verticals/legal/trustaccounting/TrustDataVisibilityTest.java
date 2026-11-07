package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalModuleGuard;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Security review (PR #1749): trust money in documents = trust_accounting module AND VIEW_TRUST.
 */
class TrustDataVisibilityTest {

  private final VerticalModuleGuard moduleGuard = mock(VerticalModuleGuard.class);
  private final TrustDataVisibility visibility = new TrustDataVisibility(moduleGuard);

  @Test
  void moduleOnAndViewTrust_visible() {
    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(true);
    assertThat(visibleWith(Set.of("VIEW_TRUST"))).isTrue();
  }

  @Test
  void moduleOnWithoutViewTrust_notVisible() {
    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(true);
    assertThat(visibleWith(Set.of("MANAGE_TRUST", "TEAM_OVERSIGHT"))).isFalse();
  }

  @Test
  void moduleOff_notVisible_evenWithViewTrust() {
    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(false);
    assertThat(visibleWith(Set.of("VIEW_TRUST"))).isFalse();
  }

  @Test
  void noBoundCapabilities_notVisible() {
    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(true);
    assertThat(visibility.visibleToCurrentMember()).isFalse();
  }

  @Test
  void withheld_onlyWhenTheModuleIsOnAndViewTrustIsMissing() {
    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(true);
    assertThat(withheldWith(Set.of("VIEW_LEGAL"))).isTrue();
    assertThat(withheldWith(Set.of("VIEW_TRUST"))).isFalse();
  }

  @Test
  void moduleOff_nothingIsWithheld() {
    when(moduleGuard.isModuleEnabled("trust_accounting")).thenReturn(false);
    assertThat(withheldWith(Set.of("VIEW_LEGAL"))).isFalse();
  }

  private boolean withheldWith(Set<String> capabilities) {
    return ScopedValue.where(RequestScopes.CAPABILITIES, capabilities)
        .call(visibility::withheldFromCurrentMember);
  }

  private boolean visibleWith(Set<String> capabilities) {
    return ScopedValue.where(RequestScopes.CAPABILITIES, capabilities)
        .call(visibility::visibleToCurrentMember);
  }
}

package io.b2mash.b2b.b2bstrawman.verticals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.exception.ModuleNotEnabledException;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerticalModuleGuardTest {

  private OrgSettingsService orgSettingsService;
  private VerticalModuleGuard guard;

  @BeforeEach
  void setUp() {
    orgSettingsService = mock(OrgSettingsService.class);
    guard = new VerticalModuleGuard(orgSettingsService);
  }

  @Test
  void requireModule_throwsWhenModuleNotEnabled() {
    when(orgSettingsService.getEnabledModulesForCurrentTenant())
        .thenReturn(List.of("court_calendar"));

    assertThatThrownBy(() -> guard.requireModule("trust_accounting"))
        .isInstanceOf(ModuleNotEnabledException.class);
  }

  @Test
  void requireModule_doesNotThrowWhenModuleEnabled() {
    when(orgSettingsService.getEnabledModulesForCurrentTenant())
        .thenReturn(List.of("trust_accounting", "court_calendar"));

    // Should not throw
    guard.requireModule("trust_accounting");
  }

  @Test
  void isModuleEnabled_returnsTrueWhenEnabled() {
    when(orgSettingsService.getEnabledModulesForCurrentTenant())
        .thenReturn(List.of("trust_accounting"));

    assertThat(guard.isModuleEnabled("trust_accounting")).isTrue();
    assertThat(guard.isModuleEnabled("court_calendar")).isFalse();
  }

  @Test
  void getEnabledModules_returnsCorrectSet() {
    when(orgSettingsService.getEnabledModulesForCurrentTenant())
        .thenReturn(List.of("trust_accounting", "conflict_check"));

    var modules = guard.getEnabledModules();

    assertThat(modules).containsExactlyInAnyOrder("trust_accounting", "conflict_check");
  }
}

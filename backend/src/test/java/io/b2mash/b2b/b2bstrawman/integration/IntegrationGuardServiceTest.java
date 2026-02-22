package io.b2mash.b2b.b2bstrawman.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntegrationGuardServiceTest {

  @Mock private OrgSettingsRepository orgSettingsRepository;

  @InjectMocks private IntegrationGuardService guardService;

  @Test
  void accounting_enabled_no_exception() {
    var settings = new OrgSettings("USD");
    settings.updateIntegrationFlags(true, false, false);
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(settings));

    assertThatCode(() -> guardService.requireEnabled(IntegrationDomain.ACCOUNTING))
        .doesNotThrowAnyException();
  }

  @Test
  void accounting_disabled_throws_403() {
    var settings = new OrgSettings("USD");
    // All flags default to false
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(settings));

    assertThatThrownBy(() -> guardService.requireEnabled(IntegrationDomain.ACCOUNTING))
        .isInstanceOf(IntegrationDisabledException.class);
  }

  @Test
  void payment_always_allowed() {
    // No mock setup needed -- PAYMENT returns early without DB access
    assertThatCode(() -> guardService.requireEnabled(IntegrationDomain.PAYMENT))
        .doesNotThrowAnyException();
  }

  @Test
  void ai_disabled_throws() {
    var settings = new OrgSettings("USD");
    settings.updateIntegrationFlags(true, false, true); // AI is false
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(settings));

    assertThatThrownBy(() -> guardService.requireEnabled(IntegrationDomain.AI))
        .isInstanceOf(IntegrationDisabledException.class);
  }

  @Test
  void no_settings_row_throws_for_non_payment() {
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());

    assertThatThrownBy(() -> guardService.requireEnabled(IntegrationDomain.DOCUMENT_SIGNING))
        .isInstanceOf(IntegrationDisabledException.class);
  }
}

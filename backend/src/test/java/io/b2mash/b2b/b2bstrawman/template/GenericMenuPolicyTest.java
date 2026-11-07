package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.seeder.AbstractPackSeeder.LoadedPack;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

/**
 * CodeRabbit (PR #1749): a failed classpath scan (getAvailablePacks() empty) must not be cached as
 * "no exclusions" — the next listing retries; a successful scan is cached.
 */
class GenericMenuPolicyTest {

  private final TemplatePackSeeder seeder = mock(TemplatePackSeeder.class);
  private final OrgSettingsRepository orgSettingsRepository = mock(OrgSettingsRepository.class);
  private final GenericMenuPolicy policy = new GenericMenuPolicy(seeder, orgSettingsRepository);

  @BeforeEach
  void legalZaTenant() {
    var settings = new OrgSettings("ZAR");
    settings.setVerticalProfile("legal-za");
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.of(settings));
  }

  @Test
  void failedScan_isNotCached_nextListingRetriesAndCachesTheSuccess() {
    when(seeder.getAvailablePacks()).thenReturn(List.of(), List.of(commonPack()));
    var summary = commonPlatformRow();

    assertThat(policy.forCurrentTenant().hiddenFromGenericMenus(summary))
        .as("failed scan: nothing known yet")
        .isFalse();
    assertThat(policy.forCurrentTenant().hiddenFromGenericMenus(summary))
        .as("retried scan sees the exclusion")
        .isTrue();
    assertThat(policy.forCurrentTenant().hiddenFromGenericMenus(summary)).isTrue();

    verify(seeder, times(2)).getAvailablePacks(); // the success is cached
  }

  private static LoadedPack<TemplatePackDefinition> commonPack() {
    var template =
        new TemplatePackTemplate(
            "project-summary",
            "Matter Summary Report",
            "PROJECT_SUMMARY",
            "PROJECT",
            "project-summary.json",
            null,
            null,
            2,
            null,
            List.of(),
            List.of("legal-za"));
    var pack =
        new TemplatePackDefinition(
            "common", 4, null, "Common Templates", null, List.of(template), null);
    return new LoadedPack<>(pack, new ByteArrayResource(new byte[0]));
  }

  private static DocumentTemplate commonPlatformRow() {
    var dt =
        new DocumentTemplate(
            TemplateEntityType.PROJECT,
            "Matter Summary Report",
            "matter-summary-report",
            TemplateCategory.PROJECT_SUMMARY,
            Map.of("type", "doc"));
    dt.setSource(TemplateSource.PLATFORM);
    dt.setPackId("common");
    dt.setPackTemplateKey("project-summary");
    return dt;
  }
}

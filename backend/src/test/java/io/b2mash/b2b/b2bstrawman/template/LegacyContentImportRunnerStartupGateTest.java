package io.b2mash.b2b.b2bstrawman.template;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantScopedRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * F-657A-2: the startup trigger of {@link LegacyContentImportRunner} is gated by {@code
 * kazi.legacy-content-import.enabled}. Off (the test profile), a context start launches no
 * background sweep over the tenant schemas; on (the default everywhere else), it still does.
 */
@ExtendWith(MockitoExtension.class)
class LegacyContentImportRunnerStartupGateTest {

  @Mock private TenantScopedRunner tenantScopedRunner;
  @Mock private DocumentTemplateRepository templateRepository;
  @Mock private ClauseRepository clauseRepository;
  @Mock private LegacyContentImporter importer;
  @Mock private TransactionTemplate transactionTemplate;

  private LegacyContentImportRunner runner(boolean enabled) {
    return new LegacyContentImportRunner(
        tenantScopedRunner,
        templateRepository,
        clauseRepository,
        importer,
        transactionTemplate,
        enabled);
  }

  @Test
  void disabled_startupTriggerSweepsNoTenant() {
    runner(false).run(new DefaultApplicationArguments());

    verify(tenantScopedRunner, after(500).never()).forEachTenant(any());
  }

  @Test
  void enabled_startupTriggerStillSweepsTheTenantsInTheBackground() {
    runner(true).run(new DefaultApplicationArguments());

    verify(tenantScopedRunner, timeout(5_000)).forEachTenant(any());
  }
}

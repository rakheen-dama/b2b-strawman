package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalBackfillMarkerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plain Mockito (no Spring context): a tenant whose backfill throws gets no marker (so it is
 * retried at the next start), one failing tenant neither stops the others nor escapes {@code run},
 * and a marked tenant is skipped without touching its schema.
 */
class PortalTaskBackfillRunnerUnitTest {

  private static final String NAME = PortalTaskBackfillRunner.NAME;

  private final OrgSchemaMapping failing =
      new OrgSchemaMapping("org_task_backfill_failing", "tenant_tbfailing0001");
  private final OrgSchemaMapping healthy =
      new OrgSchemaMapping("org_task_backfill_healthy", "tenant_tbhealthy0001");

  private OrgSchemaMappingRepository mappingRepository;
  private PortalBackfillMarkerRepository markerRepository;
  private PortalTaskBackfillService backfillService;
  private PortalTaskBackfillRunner runner;

  @BeforeEach
  void setUp() {
    mappingRepository = mock(OrgSchemaMappingRepository.class);
    markerRepository = mock(PortalBackfillMarkerRepository.class);
    backfillService = mock(PortalTaskBackfillService.class);
    runner = new PortalTaskBackfillRunner(mappingRepository, markerRepository, backfillService);
    when(backfillService.backfillTenant(
            "org_task_backfill_failing", "tenant_tbfailing0001", "primary"))
        .thenThrow(new IllegalStateException("tenant schema unreachable"));
    when(backfillService.backfillTenant(
            "org_task_backfill_healthy", "tenant_tbhealthy0001", "primary"))
        .thenReturn(9);
  }

  @Test
  void failingTenantGetsNoMarker() {
    assertThatThrownBy(() -> runner.backfillTenant(failing))
        .isInstanceOf(IllegalStateException.class);

    verify(markerRepository, never()).insertMarker(eq(NAME), eq("tenant_tbfailing0001"), anyInt());
  }

  @Test
  void runIsolatesAFailingTenantAndMarksTheOthers() {
    when(mappingRepository.findAll()).thenReturn(List.of(failing, healthy));

    assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();

    verify(markerRepository, never()).insertMarker(eq(NAME), eq("tenant_tbfailing0001"), anyInt());
    verify(markerRepository).insertMarker(NAME, "tenant_tbhealthy0001", 9);
  }

  @Test
  void runNeverThrowsEvenWhenTheMappingLookupFails() {
    when(mappingRepository.findAll()).thenThrow(new IllegalStateException("global DB down"));

    assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
  }

  @Test
  void markedTenantIsSkipped() {
    when(markerRepository.hasMarker(NAME, "tenant_tbhealthy0001")).thenReturn(true);

    assertThat(runner.backfillTenant(healthy)).isEqualTo(-1);

    verify(backfillService, never())
        .backfillTenant("org_task_backfill_healthy", "tenant_tbhealthy0001", "primary");
  }
}

package io.b2mash.b2b.b2bstrawman.portal.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plain Mockito (no Spring context): a tenant whose reconcile throws gets no backfill marker (so it
 * is retried at the next start), and one failing tenant neither stops the others nor escapes {@code
 * run}.
 */
class PortalContactDirectoryBackfillRunnerTest {

  private final OrgSchemaMapping failing =
      new OrgSchemaMapping("org_backfill_failing", "tenant_failing00001");
  private final OrgSchemaMapping healthy =
      new OrgSchemaMapping("org_backfill_healthy", "tenant_healthy00001");

  private OrgSchemaMappingRepository mappingRepository;
  private PortalContactDirectoryRepository directoryRepository;
  private PortalContactDirectoryService directoryService;
  private PortalContactDirectoryBackfillRunner runner;

  @BeforeEach
  void setUp() {
    mappingRepository = mock(OrgSchemaMappingRepository.class);
    directoryRepository = mock(PortalContactDirectoryRepository.class);
    directoryService = mock(PortalContactDirectoryService.class);
    runner =
        new PortalContactDirectoryBackfillRunner(
            mappingRepository, directoryRepository, directoryService);
    when(directoryService.reconcileOrg("org_backfill_failing", "tenant_failing00001", "primary"))
        .thenThrow(new IllegalStateException("tenant schema unreachable"));
    when(directoryService.reconcileOrg("org_backfill_healthy", "tenant_healthy00001", "primary"))
        .thenReturn(3);
  }

  @Test
  void failingTenantGetsNoMarker() {
    assertThatThrownBy(() -> runner.backfillTenant(failing))
        .isInstanceOf(IllegalStateException.class);

    verify(directoryRepository, never()).insertBackfillMarker(eq("tenant_failing00001"), anyInt());
  }

  @Test
  void runIsolatesAFailingTenantAndMarksTheOthers() {
    when(mappingRepository.findAll()).thenReturn(List.of(failing, healthy));

    assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();

    verify(directoryRepository, never()).insertBackfillMarker(eq("tenant_failing00001"), anyInt());
    verify(directoryRepository).insertBackfillMarker("tenant_healthy00001", 3);
  }

  @Test
  void markedTenantIsSkipped() {
    when(directoryRepository.hasBackfillMarker("tenant_healthy00001")).thenReturn(true);

    assertThat(runner.backfillTenant(healthy)).isEqualTo(-1);

    verify(directoryService, never())
        .reconcileOrg("org_backfill_healthy", "tenant_healthy00001", "primary");
  }
}

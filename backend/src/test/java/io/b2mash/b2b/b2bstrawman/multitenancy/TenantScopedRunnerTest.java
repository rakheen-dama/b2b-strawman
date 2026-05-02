package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TenantScopedRunnerTest {

  private static OrgSchemaMapping mapping(String schema, String orgId) {
    OrgSchemaMapping m = mock(OrgSchemaMapping.class);
    when(m.getSchemaName()).thenReturn(schema);
    when(m.getExternalOrgId()).thenReturn(orgId);
    return m;
  }

  @Test
  void forEachTenant_invokesActionWithTenantAndOrgBoundPerMapping() {
    // Build the mapping list BEFORE the when().thenReturn() call. Nesting mock(..) +
    // when(..).thenReturn(..) inside List.of(..) inside thenReturn(..) trips Mockito's
    // UnfinishedStubbingException because the inner stubbings interleave with the outer.
    List<OrgSchemaMapping> mappings =
        List.of(mapping("tenant_aaa", "org_one"), mapping("tenant_bbb", "org_two"));
    OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
    when(repo.findAll()).thenReturn(mappings);

    TenantScopedRunner runner = new TenantScopedRunner(repo);
    AtomicInteger calls = new AtomicInteger();

    int succeeded =
        runner.forEachTenant(
            (tenantId, orgId) -> {
              calls.incrementAndGet();
              // Inside the action, scope is bound:
              assertThat(RequestScopes.TENANT_ID.get()).isEqualTo(tenantId);
              assertThat(RequestScopes.ORG_ID.get()).isEqualTo(orgId);
            });

    assertThat(calls.get()).isEqualTo(2);
    assertThat(succeeded).isEqualTo(2);
  }

  @Test
  void forEachTenant_perTenantExceptionDoesNotAbortIteration() {
    List<OrgSchemaMapping> mappings =
        List.of(
            mapping("tenant_aaa", "org_one"),
            mapping("tenant_bbb", "org_two"),
            mapping("tenant_ccc", "org_three"));
    OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
    when(repo.findAll()).thenReturn(mappings);

    TenantScopedRunner runner = new TenantScopedRunner(repo);
    AtomicInteger calls = new AtomicInteger();

    int succeeded =
        runner.forEachTenant(
            (tenantId, orgId) -> {
              calls.incrementAndGet();
              if ("tenant_bbb".equals(tenantId)) {
                throw new RuntimeException("simulated failure");
              }
            });

    assertThat(calls.get()).isEqualTo(3); // all three were attempted
    assertThat(succeeded).isEqualTo(2); // two succeeded
  }

  @Test
  void forEachTenant_emptyMappingList_returnsZero() {
    OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
    when(repo.findAll()).thenReturn(List.of());

    TenantScopedRunner runner = new TenantScopedRunner(repo);
    AtomicInteger calls = new AtomicInteger();

    int succeeded = runner.forEachTenant((t, o) -> calls.incrementAndGet());

    assertThat(calls.get()).isEqualTo(0);
    assertThat(succeeded).isEqualTo(0);
  }

  @Test
  void forEachTenant_nullAction_throwsNullPointerException() {
    OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
    TenantScopedRunner runner = new TenantScopedRunner(repo);
    assertThatThrownBy(() -> runner.forEachTenant(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void forEachTenant_unbindsScopeAfterIteration() {
    List<OrgSchemaMapping> mappings = List.of(mapping("tenant_aaa", "org_one"));
    OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
    when(repo.findAll()).thenReturn(mappings);

    TenantScopedRunner runner = new TenantScopedRunner(repo);
    runner.forEachTenant((t, o) -> {});

    assertThat(RequestScopes.TENANT_ID.isBound()).isFalse();
    assertThat(RequestScopes.ORG_ID.isBound()).isFalse();
  }
}

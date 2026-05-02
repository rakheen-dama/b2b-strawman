package io.b2mash.b2b.b2bstrawman.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TenantDiscoveryHelperTest {

  private static OrgSchemaMapping mapping(String schema, String orgId) {
    OrgSchemaMapping m = mock(OrgSchemaMapping.class);
    when(m.getSchemaName()).thenReturn(schema);
    when(m.getExternalOrgId()).thenReturn(orgId);
    return m;
  }

  @Test
  void findInTenants_emptyMappingList_returnsEmpty() {
    OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
    when(repo.findAll()).thenReturn(List.of());

    TenantDiscoveryHelper helper = new TenantDiscoveryHelper(repo);
    AtomicInteger calls = new AtomicInteger();

    Optional<TenantDiscoveryHelper.TenantMatch<String>> result =
        helper.findInTenants(
            () -> {
              calls.incrementAndGet();
              return Optional.of("never reached");
            });

    assertThat(result).isEmpty();
    assertThat(calls.get()).isEqualTo(0);
  }

  @Test
  void findInTenants_firstTenantMatches_returnsFirstWithoutVisitingRest() {
    List<OrgSchemaMapping> mappings =
        List.of(
            mapping("tenant_aaa", "org_one"),
            mapping("tenant_bbb", "org_two"),
            mapping("tenant_ccc", "org_three"));
    OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
    when(repo.findAll()).thenReturn(mappings);

    TenantDiscoveryHelper helper = new TenantDiscoveryHelper(repo);
    AtomicInteger calls = new AtomicInteger();

    Optional<TenantDiscoveryHelper.TenantMatch<String>> result =
        helper.findInTenants(
            () -> {
              calls.incrementAndGet();
              return Optional.of("found-in-" + RequestScopes.TENANT_ID.get());
            });

    assertThat(result).isPresent();
    assertThat(result.get().tenantId()).isEqualTo("tenant_aaa");
    assertThat(result.get().orgId()).isEqualTo("org_one");
    assertThat(result.get().value()).isEqualTo("found-in-tenant_aaa");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void findInTenants_lastTenantMatches_visitsAllAndReturnsLast() {
    List<OrgSchemaMapping> mappings =
        List.of(
            mapping("tenant_aaa", "org_one"),
            mapping("tenant_bbb", "org_two"),
            mapping("tenant_ccc", "org_three"));
    OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
    when(repo.findAll()).thenReturn(mappings);

    TenantDiscoveryHelper helper = new TenantDiscoveryHelper(repo);
    AtomicInteger calls = new AtomicInteger();

    Optional<TenantDiscoveryHelper.TenantMatch<String>> result =
        helper.findInTenants(
            () -> {
              calls.incrementAndGet();
              if ("tenant_ccc".equals(RequestScopes.TENANT_ID.get())) {
                return Optional.of("found-here");
              }
              return Optional.empty();
            });

    assertThat(result).isPresent();
    assertThat(result.get().tenantId()).isEqualTo("tenant_ccc");
    assertThat(result.get().value()).isEqualTo("found-here");
    assertThat(calls.get()).isEqualTo(3);
  }

  @Test
  void findInTenants_noTenantMatches_returnsEmpty() {
    List<OrgSchemaMapping> mappings =
        List.of(mapping("tenant_aaa", "org_one"), mapping("tenant_bbb", "org_two"));
    OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
    when(repo.findAll()).thenReturn(mappings);

    TenantDiscoveryHelper helper = new TenantDiscoveryHelper(repo);
    AtomicInteger calls = new AtomicInteger();

    Optional<TenantDiscoveryHelper.TenantMatch<String>> result =
        helper.findInTenants(
            () -> {
              calls.incrementAndGet();
              return Optional.empty();
            });

    assertThat(result).isEmpty();
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void findInTenants_perTenantExceptionContinuesIteration() {
    List<OrgSchemaMapping> mappings =
        List.of(
            mapping("tenant_aaa", "org_one"),
            mapping("tenant_bbb", "org_two"),
            mapping("tenant_ccc", "org_three"));
    OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
    when(repo.findAll()).thenReturn(mappings);

    TenantDiscoveryHelper helper = new TenantDiscoveryHelper(repo);
    AtomicInteger calls = new AtomicInteger();

    Optional<TenantDiscoveryHelper.TenantMatch<String>> result =
        helper.findInTenants(
            () -> {
              calls.incrementAndGet();
              String tid = RequestScopes.TENANT_ID.get();
              if ("tenant_bbb".equals(tid)) {
                throw new RuntimeException("simulated failure");
              }
              if ("tenant_ccc".equals(tid)) {
                return Optional.of("eventually-found");
              }
              return Optional.empty();
            });

    assertThat(result).isPresent();
    assertThat(result.get().tenantId()).isEqualTo("tenant_ccc");
    assertThat(calls.get()).isEqualTo(3); // all three attempted; bbb threw, ccc succeeded
  }

  @Test
  void findInTenants_nullFinder_throwsNullPointerException() {
    OrgSchemaMappingRepository repo = mock(OrgSchemaMappingRepository.class);
    TenantDiscoveryHelper helper = new TenantDiscoveryHelper(repo);
    assertThatThrownBy(() -> helper.findInTenants(null)).isInstanceOf(NullPointerException.class);
  }
}

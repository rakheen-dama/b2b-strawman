package io.b2mash.b2b.b2bstrawman.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.billing.BillingMethod;
import io.b2mash.b2b.b2bstrawman.billing.Subscription;
import io.b2mash.b2b.b2bstrawman.billing.SubscriptionRepository;
import io.b2mash.b2b.b2bstrawman.billing.SubscriptionStatusCache;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalBackfillMarkerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.Organization;
import io.b2mash.b2b.b2bstrawman.provisioning.OrganizationRepository;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fault injection for the best-effort portal backfill marker delete (Phase 89 633A, B3): a failing
 * {@link PortalBackfillMarkerRepository} cannot be produced with the real repository, so this case
 * lives here rather than in the {@code DemoCleanupServiceTest} integration test (which covers the
 * real-repository success path).
 */
class DemoCleanupServiceUnitTest {

  private static final String EXTERNAL_ORG_ID = "cleanup-unit-org";
  private static final String SCHEMA = "tenant_0123456789ab";
  private static final String ORG_NAME = "Cleanup Unit Firm";

  @Test
  void aFailedMarkerDeleteDoesNotFailTheSchemaCleanup() throws Exception {
    UUID orgId = UUID.randomUUID();
    var org = mock(Organization.class);
    when(org.getId()).thenReturn(orgId);
    when(org.getName()).thenReturn(ORG_NAME);
    when(org.getExternalOrgId()).thenReturn(EXTERNAL_ORG_ID);
    var organizationRepository = mock(OrganizationRepository.class);
    when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

    var sub = mock(Subscription.class);
    when(sub.getId()).thenReturn(UUID.randomUUID());
    when(sub.getBillingMethod()).thenReturn(BillingMethod.PILOT);
    var subscriptionRepository = mock(SubscriptionRepository.class);
    when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));

    var mapping = mock(OrgSchemaMapping.class);
    when(mapping.getSchemaName()).thenReturn(SCHEMA);
    var orgSchemaMappingRepository = mock(OrgSchemaMappingRepository.class);
    when(orgSchemaMappingRepository.findByExternalOrgId(EXTERNAL_ORG_ID))
        .thenReturn(Optional.of(mapping));

    var statement = mock(Statement.class);
    var connection = mock(Connection.class);
    when(connection.createStatement()).thenReturn(statement);
    var migrationDataSource = mock(DataSource.class);
    when(migrationDataSource.getConnection()).thenReturn(connection);

    var failingMarkers = mock(PortalBackfillMarkerRepository.class);
    when(failingMarkers.deleteForSchema(anyString()))
        .thenThrow(new IllegalStateException("portal datasource down"));

    var service =
        new DemoCleanupService(
            null, // Keycloak not configured — that step is skipped, not under test
            organizationRepository,
            subscriptionRepository,
            mock(SubscriptionStatusCache.class),
            orgSchemaMappingRepository,
            migrationDataSource,
            mock(JdbcTemplate.class),
            new TransactionTemplate(mock(PlatformTransactionManager.class)),
            null,
            failingMarkers);

    var response = service.cleanup(orgId, ORG_NAME, "test-admin");

    verify(statement).execute("DROP SCHEMA IF EXISTS \"" + SCHEMA + "\" CASCADE");
    verify(failingMarkers).deleteForSchema(SCHEMA);
    assertThat(response.schemaCleaned()).isTrue();
    assertThat(response.publicRecordsCleaned()).isTrue();
    assertThat(response.errors()).noneMatch(e -> e.contains("Schema cleanup failed"));
  }
}

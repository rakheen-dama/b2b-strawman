package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.billing.SubscriptionService;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackSeeder;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackSeeder;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTest {

  @Mock private OrganizationRepository organizationRepository;
  @Mock private OrgSchemaMappingRepository mappingRepository;
  @Mock private DataSource migrationDataSource;
  @Mock private SubscriptionService subscriptionService;
  @Mock private FieldPackSeeder fieldPackSeeder;
  @Mock private TemplatePackSeeder templatePackSeeder;

  private TenantProvisioningService service;

  @BeforeEach
  void setUp() {
    // Spy so we can stub runTenantMigrations (which calls Flyway static API)
    service =
        spy(
            new TenantProvisioningService(
                organizationRepository,
                mappingRepository,
                migrationDataSource,
                subscriptionService,
                fieldPackSeeder,
                templatePackSeeder));
  }

  @Test
  void provisionTenant_returnsAlreadyProvisionedWhenMappingExists() {
    var mapping = new OrgSchemaMapping("org_123", "tenant_abcdef012345");
    when(mappingRepository.findByClerkOrgId("org_123")).thenReturn(Optional.of(mapping));

    var result = service.provisionTenant("org_123", "Test Org");

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isTrue();
    assertThat(result.schemaName()).isEqualTo("tenant_abcdef012345");
    verify(organizationRepository, never()).save(any());
  }

  @Test
  void provisionTenant_starterMapsToSharedSchema() {
    when(mappingRepository.findByClerkOrgId("org_new")).thenReturn(Optional.empty());
    when(organizationRepository.findByClerkOrgId("org_new")).thenReturn(Optional.empty());

    var org = new Organization("org_new", "New Org");
    // Default tier is STARTER
    when(organizationRepository.save(any(Organization.class))).thenReturn(org);

    when(mappingRepository.save(any(OrgSchemaMapping.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var result = service.provisionTenant("org_new", "New Org");

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isFalse();
    assertThat(result.schemaName()).isEqualTo("tenant_shared");
    assertThat(org.getProvisioningStatus()).isEqualTo(Organization.ProvisioningStatus.COMPLETED);
  }

  @Test
  void provisionTenant_proCreatesNewDedicatedSchema() throws SQLException {
    when(mappingRepository.findByClerkOrgId("org_pro")).thenReturn(Optional.empty());

    var org = new Organization("org_pro", "Pro Org");
    org.updatePlan(Tier.PRO, "pro_plan");
    when(organizationRepository.findByClerkOrgId("org_pro")).thenReturn(Optional.of(org));
    when(organizationRepository.save(org)).thenReturn(org);

    var mockConn = mock(Connection.class);
    var mockStmt = mock(Statement.class);
    when(migrationDataSource.getConnection()).thenReturn(mockConn);
    when(mockConn.createStatement()).thenReturn(mockStmt);

    when(mappingRepository.save(any(OrgSchemaMapping.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    doNothing().when(service).runTenantMigrations(anyString());

    var result = service.provisionTenant("org_pro", "Pro Org");

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isFalse();
    assertThat(result.schemaName()).matches("tenant_[0-9a-f]{12}");
    verify(mockStmt).execute(anyString());
    assertThat(org.getProvisioningStatus()).isEqualTo(Organization.ProvisioningStatus.COMPLETED);
  }

  @Test
  void provisionTenant_proMarksOrgFailedOnSchemaCreationError() throws SQLException {
    when(mappingRepository.findByClerkOrgId("org_fail")).thenReturn(Optional.empty());

    var org = new Organization("org_fail", "Fail Org");
    org.updatePlan(Tier.PRO, "pro_plan");
    when(organizationRepository.findByClerkOrgId("org_fail")).thenReturn(Optional.of(org));
    when(organizationRepository.save(any(Organization.class))).thenReturn(org);

    when(migrationDataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

    assertThatThrownBy(() -> service.provisionTenant("org_fail", "Fail Org"))
        .isInstanceOf(ProvisioningException.class)
        .hasMessageContaining("org_fail");

    assertThat(org.getProvisioningStatus()).isEqualTo(Organization.ProvisioningStatus.FAILED);
  }

  @Test
  void provisionTenant_idempotentMappingCheckInCreateMapping() {
    when(mappingRepository.findByClerkOrgId("org_idem"))
        .thenReturn(Optional.empty()) // first call in main method
        .thenReturn(
            Optional.of(new OrgSchemaMapping("org_idem", "tenant_shared"))); // in createMapping

    var org = new Organization("org_idem", "Idem Org");
    when(organizationRepository.findByClerkOrgId("org_idem")).thenReturn(Optional.empty());
    when(organizationRepository.save(any(Organization.class))).thenReturn(org);

    var result = service.provisionTenant("org_idem", "Idem Org");

    assertThat(result.success()).isTrue();
    verify(mappingRepository, never()).save(any(OrgSchemaMapping.class));
  }
}

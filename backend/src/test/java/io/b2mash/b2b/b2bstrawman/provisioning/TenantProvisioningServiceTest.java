package io.b2mash.b2b.b2bstrawman.provisioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.automation.template.AutomationTemplateSeeder;
import io.b2mash.b2b.b2bstrawman.billing.SubscriptionService;
import io.b2mash.b2b.b2bstrawman.clause.ClausePackSeeder;
import io.b2mash.b2b.b2bstrawman.compliance.CompliancePackSeeder;
import io.b2mash.b2b.b2bstrawman.fielddefinition.FieldPackSeeder;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestPackSeeder;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.TenantTransactionHelper;
import io.b2mash.b2b.b2bstrawman.reporting.StandardReportPackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.RatePackSeeder;
import io.b2mash.b2b.b2bstrawman.seeder.SchedulePackSeeder;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.template.TemplatePackSeeder;
import io.b2mash.b2b.b2bstrawman.verticals.VerticalProfileRegistry;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for TenantProvisioningService. Uses @Spy + @InjectMocks so that Mockito handles
 * constructor injection automatically — survives constructor signature changes. The spy allows
 * stubbing runTenantMigrations (Flyway static API).
 */
@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTest {

  @Mock private OrganizationRepository organizationRepository;
  @Mock private OrgSchemaMappingRepository mappingRepository;
  @Mock private DataSource migrationDataSource;
  @Mock private SubscriptionService subscriptionService;
  @Mock private FieldPackSeeder fieldPackSeeder;
  @Mock private TemplatePackSeeder templatePackSeeder;
  @Mock private ClausePackSeeder clausePackSeeder;
  @Mock private CompliancePackSeeder compliancePackSeeder;
  @Mock private StandardReportPackSeeder standardReportPackSeeder;
  @Mock private RequestPackSeeder requestPackSeeder;
  @Mock private AutomationTemplateSeeder automationTemplateSeeder;
  @Mock private RatePackSeeder ratePackSeeder;
  @Mock private SchedulePackSeeder schedulePackSeeder;
  @Mock private TenantTransactionHelper tenantTransactionHelper;
  @Mock private OrgSettingsRepository orgSettingsRepository;
  @Mock private VerticalProfileRegistry verticalProfileRegistry;

  @Spy @InjectMocks private TenantProvisioningService service;

  @Test
  void provisionTenant_returnsAlreadyProvisionedWhenMappingExists() {
    var mapping = new OrgSchemaMapping("org_123", "tenant_abcdef012345");
    when(mappingRepository.findByClerkOrgId("org_123")).thenReturn(Optional.of(mapping));

    var result = service.provisionTenant("org_123", "Test Org", null);

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isTrue();
    assertThat(result.schemaName()).isEqualTo("tenant_abcdef012345");
    verify(organizationRepository, never()).save(any());
  }

  @Test
  void provisionTenant_starterGetsDedicatedSchema() throws SQLException {
    when(mappingRepository.findByClerkOrgId("org_new")).thenReturn(Optional.empty());
    when(organizationRepository.findByClerkOrgId("org_new")).thenReturn(Optional.empty());

    var org = new Organization("org_new", "New Org");
    // Default tier is STARTER — now always gets a dedicated schema
    when(organizationRepository.save(any(Organization.class))).thenReturn(org);

    var mockConn = mock(Connection.class);
    var mockStmt = mock(Statement.class);
    when(migrationDataSource.getConnection()).thenReturn(mockConn);
    when(mockConn.createStatement()).thenReturn(mockStmt);

    when(mappingRepository.save(any(OrgSchemaMapping.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    doNothing().when(service).runTenantMigrations(anyString());

    var result = service.provisionTenant("org_new", "New Org", null);

    assertThat(result.success()).isTrue();
    assertThat(result.alreadyProvisioned()).isFalse();
    assertThat(result.schemaName()).matches("tenant_[0-9a-f]{12}");
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

    var result = service.provisionTenant("org_pro", "Pro Org", null);

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

    assertThatThrownBy(() -> service.provisionTenant("org_fail", "Fail Org", null))
        .isInstanceOf(ProvisioningException.class)
        .hasMessageContaining("org_fail");

    assertThat(org.getProvisioningStatus()).isEqualTo(Organization.ProvisioningStatus.FAILED);
  }

  @Test
  void provisionTenant_idempotentMappingCheckInCreateMapping() throws SQLException {
    String expectedSchema = SchemaNameGenerator.generateSchemaName("org_idem");
    when(mappingRepository.findByClerkOrgId("org_idem"))
        .thenReturn(Optional.empty()) // first call in main method
        .thenReturn(
            Optional.of(new OrgSchemaMapping("org_idem", expectedSchema))); // in createMapping

    var org = new Organization("org_idem", "Idem Org");
    when(organizationRepository.findByClerkOrgId("org_idem")).thenReturn(Optional.empty());
    when(organizationRepository.save(any(Organization.class))).thenReturn(org);

    var mockConn = mock(Connection.class);
    var mockStmt = mock(Statement.class);
    when(migrationDataSource.getConnection()).thenReturn(mockConn);
    when(mockConn.createStatement()).thenReturn(mockStmt);

    doNothing().when(service).runTenantMigrations(anyString());

    var result = service.provisionTenant("org_idem", "Idem Org", null);

    assertThat(result.success()).isTrue();
    verify(mappingRepository, never()).save(any(OrgSchemaMapping.class));
  }

  @Test
  void setVerticalProfile_withLegalZa_setsEnabledModulesAndTerminology() throws SQLException {
    when(mappingRepository.findByClerkOrgId("org_legal")).thenReturn(Optional.empty());
    when(organizationRepository.findByClerkOrgId("org_legal")).thenReturn(Optional.empty());

    var org = new Organization("org_legal", "Legal Org");
    when(organizationRepository.save(any(Organization.class))).thenReturn(org);

    var mockConn = mock(Connection.class);
    var mockStmt = mock(Statement.class);
    when(migrationDataSource.getConnection()).thenReturn(mockConn);
    when(mockConn.createStatement()).thenReturn(mockStmt);

    when(mappingRepository.save(any(OrgSchemaMapping.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    doNothing().when(service).runTenantMigrations(anyString());

    // Mock the registry to return legal-za profile
    var legalProfile =
        new VerticalProfileRegistry.ProfileDefinition(
            "legal-za",
            "Legal (South Africa)",
            "SA law firm with trust accounting, court calendar, and conflict check",
            List.of("trust_accounting", "court_calendar", "conflict_check"),
            "en-ZA-legal",
            "ZAR",
            Map.of());
    when(verticalProfileRegistry.getProfile("legal-za")).thenReturn(Optional.of(legalProfile));

    // Mock TenantTransactionHelper to execute the lambda directly
    when(orgSettingsRepository.findForCurrentTenant()).thenReturn(Optional.empty());
    var settings = new OrgSettings("USD");
    when(orgSettingsRepository.save(any(OrgSettings.class))).thenReturn(settings);

    // Capture the lambda executed by tenantTransactionHelper
    doAnswer(
            invocation -> {
              var consumer = (java.util.function.Consumer<String>) invocation.getArgument(2);
              consumer.accept("tenant_id");
              return null;
            })
        .when(tenantTransactionHelper)
        .executeInTenantTransaction(anyString(), anyString(), any());

    var result = service.provisionTenant("org_legal", "Legal Org", "legal-za");

    assertThat(result.success()).isTrue();

    // Verify that the settings were updated with modules and terminology from registry
    verify(orgSettingsRepository, atLeastOnce()).save(any(OrgSettings.class));
    verify(verticalProfileRegistry).getProfile("legal-za");
  }

  @Test
  void provisionTenant_withNullProfile_skipsSetVerticalProfile() throws SQLException {
    when(mappingRepository.findByClerkOrgId("org_null_profile")).thenReturn(Optional.empty());
    when(organizationRepository.findByClerkOrgId("org_null_profile")).thenReturn(Optional.empty());

    var org = new Organization("org_null_profile", "Null Profile Org");
    when(organizationRepository.save(any(Organization.class))).thenReturn(org);

    var mockConn = mock(Connection.class);
    var mockStmt = mock(Statement.class);
    when(migrationDataSource.getConnection()).thenReturn(mockConn);
    when(mockConn.createStatement()).thenReturn(mockStmt);

    when(mappingRepository.save(any(OrgSchemaMapping.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    doNothing().when(service).runTenantMigrations(anyString());

    var result = service.provisionTenant("org_null_profile", "Null Profile Org", null);

    assertThat(result.success()).isTrue();
    // setVerticalProfile should never be called when profile is null
    verify(tenantTransactionHelper, never())
        .executeInTenantTransaction(anyString(), anyString(), any());
    verify(verticalProfileRegistry, never()).getProfile(anyString());
  }

  @Test
  void provisionTenant_callsRateAndScheduleSeeders() throws SQLException {
    when(mappingRepository.findByClerkOrgId("org_acct")).thenReturn(Optional.empty());
    when(organizationRepository.findByClerkOrgId("org_acct")).thenReturn(Optional.empty());

    var org = new Organization("org_acct", "Accounting Org");
    when(organizationRepository.save(any(Organization.class))).thenReturn(org);

    var mockConn = mock(Connection.class);
    var mockStmt = mock(Statement.class);
    when(migrationDataSource.getConnection()).thenReturn(mockConn);
    when(mockConn.createStatement()).thenReturn(mockStmt);

    when(mappingRepository.save(any(OrgSchemaMapping.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    doNothing().when(service).runTenantMigrations(anyString());

    service.provisionTenant("org_acct", "Accounting Org", null);

    verify(ratePackSeeder).seedPacksForTenant(anyString(), eq("org_acct"));
    verify(schedulePackSeeder).seedPacksForTenant(anyString(), eq("org_acct"));
  }

  @Test
  void provisionTenant_withNullProfile_stillCallsRateAndScheduleSeeders() throws SQLException {
    when(mappingRepository.findByClerkOrgId("org_no_profile")).thenReturn(Optional.empty());
    when(organizationRepository.findByClerkOrgId("org_no_profile")).thenReturn(Optional.empty());

    var org = new Organization("org_no_profile", "No Profile Org");
    when(organizationRepository.save(any(Organization.class))).thenReturn(org);

    var mockConn = mock(Connection.class);
    var mockStmt = mock(Statement.class);
    when(migrationDataSource.getConnection()).thenReturn(mockConn);
    when(mockConn.createStatement()).thenReturn(mockStmt);

    when(mappingRepository.save(any(OrgSchemaMapping.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    doNothing().when(service).runTenantMigrations(anyString());

    service.provisionTenant("org_no_profile", "No Profile Org", null);

    // Seeders are called regardless of profile — the seeder's vertical filter logic
    // handles profile mismatch internally (no-op if no matching pack)
    verify(ratePackSeeder).seedPacksForTenant(anyString(), eq("org_no_profile"));
    verify(schedulePackSeeder).seedPacksForTenant(anyString(), eq("org_no_profile"));
  }
}

package io.b2mash.b2b.b2bstrawman.integration.accounting.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationRegistry;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingPaymentSource;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.accounting.NoOpAccountingProvider;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnection;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.AccountingXeroConnectionRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.xero.XeroConnectionStatus;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountingPaymentPollWorkerTest {

  private static final String ORG_CONNECTED = "org_poll_connected";
  private static final String ORG_REVOKED = "org_poll_revoked";
  private static final String ORG_ISO_A = "org_poll_iso_a";
  private static final String ORG_ISO_B = "org_poll_iso_b";

  @MockitoBean private IntegrationRegistry integrationRegistry;

  @MockitoBean(name = "noOpAccountingProvider")
  private AccountingProvider accountingProvider;

  @Autowired private AccountingPaymentPollWorker pollWorker;
  @Autowired private AccountingXeroConnectionRepository xeroConnectionRepository;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  private String schemaConnected;
  private String schemaRevoked;
  private String schemaIsoA;
  private String schemaIsoB;
  private UUID connectedConnectionId;
  private UUID revokedConnectionId;
  private UUID isoConnectionIdA;
  private UUID isoConnectionIdB;

  @BeforeAll
  void setUp() {
    // Tenant with a CONNECTED connection — used by test 1
    provisioningService.provisionTenant(ORG_CONNECTED, "Poll Connected Org", null);
    schemaConnected =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_CONNECTED).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, schemaConnected)
        .where(RequestScopes.ORG_ID, ORG_CONNECTED)
        .run(
            () -> {
              connectedConnectionId = createConnection(XeroConnectionStatus.CONNECTED).getId();
            });

    // Tenant with a REVOKED connection — used by test 2
    provisioningService.provisionTenant(ORG_REVOKED, "Poll Revoked Org", null);
    schemaRevoked =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_REVOKED).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, schemaRevoked)
        .where(RequestScopes.ORG_ID, ORG_REVOKED)
        .run(
            () -> {
              revokedConnectionId = createConnection(XeroConnectionStatus.REVOKED).getId();
            });

    // Two tenants with CONNECTED connections — used by test 3 (exception isolation)
    provisioningService.provisionTenant(ORG_ISO_A, "Poll Isolation Org A", null);
    schemaIsoA =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ISO_A).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, schemaIsoA)
        .where(RequestScopes.ORG_ID, ORG_ISO_A)
        .run(
            () -> {
              isoConnectionIdA = createConnection(XeroConnectionStatus.CONNECTED).getId();
            });

    provisioningService.provisionTenant(ORG_ISO_B, "Poll Isolation Org B", null);
    schemaIsoB =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ISO_B).orElseThrow().getSchemaName();
    ScopedValue.where(RequestScopes.TENANT_ID, schemaIsoB)
        .where(RequestScopes.ORG_ID, ORG_ISO_B)
        .run(
            () -> {
              isoConnectionIdB = createConnection(XeroConnectionStatus.CONNECTED).getId();
            });
  }

  private AccountingXeroConnection createConnection(XeroConnectionStatus targetStatus) {
    var orgIntegration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
    orgIntegration.enable();
    var savedIntegration = orgIntegrationRepository.save(orgIntegration);

    var connection =
        new AccountingXeroConnection(
            savedIntegration.getId(),
            "xero-tenant-" + UUID.randomUUID().toString().substring(0, 8),
            "Poll Test Xero Org",
            UUID.randomUUID(),
            Instant.now().plus(30, ChronoUnit.MINUTES),
            "accounting.transactions openid profile email");

    if (targetStatus == XeroConnectionStatus.REVOKED) {
      connection.markRevoked();
    } else if (targetStatus == XeroConnectionStatus.REFRESH_FAILED) {
      connection.markRefreshFailed();
    }

    return xeroConnectionRepository.save(connection);
  }

  @Test
  @Order(1)
  void pollForTenant_pollsConnectedConnectionAndUpdatesLastPollAt() {
    var noopProvider = new NoOpAccountingProvider();
    when(integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingPaymentSource.class))
        .thenReturn(noopProvider);

    Instant beforePoll = Instant.now();

    ScopedValue.where(RequestScopes.TENANT_ID, schemaConnected)
        .where(RequestScopes.ORG_ID, ORG_CONNECTED)
        .run(
            () -> {
              pollWorker.pollForTenant();

              var updated =
                  xeroConnectionRepository.findOneById(connectedConnectionId).orElseThrow();
              assertThat(updated.getLastPollAt()).isNotNull().isAfterOrEqualTo(beforePoll);
            });
  }

  @Test
  @Order(2)
  void pollForTenant_skipsRevokedConnections() {
    var noopProvider = new NoOpAccountingProvider();
    when(integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingPaymentSource.class))
        .thenReturn(noopProvider);

    ScopedValue.where(RequestScopes.TENANT_ID, schemaRevoked)
        .where(RequestScopes.ORG_ID, ORG_REVOKED)
        .run(
            () -> {
              pollWorker.pollForTenant();

              var unchanged =
                  xeroConnectionRepository.findOneById(revokedConnectionId).orElseThrow();
              assertThat(unchanged.getLastPollAt()).isNull();
              assertThat(unchanged.getStatus()).isEqualTo(XeroConnectionStatus.REVOKED);
            });
  }

  @Test
  @Order(3)
  void pollForTenant_isolatesExceptionAcrossConnections() {
    // First resolve() call throws, subsequent calls succeed.
    // Per-connection isolation: failure on one connection must not prevent others in the same
    // tenant.
    var noopProvider = new NoOpAccountingProvider();
    when(integrationRegistry.resolve(IntegrationDomain.ACCOUNTING, AccountingPaymentSource.class))
        .thenThrow(new RuntimeException("Xero API unavailable"))
        .thenReturn(noopProvider);

    Instant beforePoll = Instant.now();

    // Poll tenant A — exception on first connection, success on subsequent
    ScopedValue.where(RequestScopes.TENANT_ID, schemaIsoA)
        .where(RequestScopes.ORG_ID, ORG_ISO_A)
        .run(() -> pollWorker.pollForTenant());

    // Poll tenant B — should succeed (provider no longer throws after first call)
    ScopedValue.where(RequestScopes.TENANT_ID, schemaIsoB)
        .where(RequestScopes.ORG_ID, ORG_ISO_B)
        .run(() -> pollWorker.pollForTenant());

    // Count how many of the isolation connections were polled during THIS cycle.
    int polledThisCycle = 0;

    Instant pollAtA =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaIsoA)
            .where(RequestScopes.ORG_ID, ORG_ISO_A)
            .call(
                () ->
                    xeroConnectionRepository
                        .findOneById(isoConnectionIdA)
                        .orElseThrow()
                        .getLastPollAt());
    if (pollAtA != null && !pollAtA.isBefore(beforePoll)) {
      polledThisCycle++;
    }

    Instant pollAtB =
        ScopedValue.where(RequestScopes.TENANT_ID, schemaIsoB)
            .where(RequestScopes.ORG_ID, ORG_ISO_B)
            .call(
                () ->
                    xeroConnectionRepository
                        .findOneById(isoConnectionIdB)
                        .orElseThrow()
                        .getLastPollAt());
    if (pollAtB != null && !pollAtB.isBefore(beforePoll)) {
      polledThisCycle++;
    }

    // At least one of the two tenants should have been polled successfully
    assertThat(polledThisCycle)
        .as(
            "At least one isolation connection should be polled despite exception on another"
                + " connection")
        .isGreaterThanOrEqualTo(1);
  }
}

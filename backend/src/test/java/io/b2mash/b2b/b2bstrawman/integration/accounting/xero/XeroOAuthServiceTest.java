package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XeroOAuthServiceTest {

  private static final String ORG_ID = "org_xero_oauth_test";

  @Autowired private XeroOAuthService xeroOAuthService;
  @Autowired private XeroProperties xeroProperties;
  @Autowired private SecretStore secretStore;
  @Autowired private AccountingXeroConnectionRepository connectionRepository;
  @Autowired private OrgIntegrationRepository orgIntegrationRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;

  @MockitoBean private XeroApiClient xeroApiClient;

  @MockitoBean(name = "xeroTokenClient")
  private RestClient tokenClient;

  private String tenantSchema;
  private UUID sharedIntegrationId;

  @BeforeAll
  void setUp() {
    provisioningService.provisionTenant(ORG_ID, "Xero OAuth Test Org", null);
    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create a shared OrgIntegration for all tests (unique constraint on domain)
    var ref = new AtomicReference<UUID>();
    runInTenant(
        () -> {
          var integration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
          integration.enable();
          integration = orgIntegrationRepository.save(integration);
          ref.set(integration.getId());
        });
    sharedIntegrationId = ref.get();
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .run(action);
  }

  /** Creates a CONNECTED AccountingXeroConnection linked to the shared OrgIntegration. */
  private AccountingXeroConnection createConnection(String suffix) {
    var connection =
        new AccountingXeroConnection(
            sharedIntegrationId,
            "xero-tenant-" + suffix,
            suffix + " Test Org",
            UUID.randomUUID(),
            Instant.now().plus(30, ChronoUnit.MINUTES),
            "offline_access openid profile email accounting.transactions accounting.contacts");
    return connectionRepository.save(connection);
  }

  private void mockTokenClientSuccess() {
    var mockRequestBodyUriSpec =
        org.mockito.Mockito.mock(
            RestClient.RequestBodyUriSpec.class, org.mockito.Answers.RETURNS_SELF);
    var mockResponseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);

    when(tokenClient.post()).thenReturn(mockRequestBodyUriSpec);
    when(mockRequestBodyUriSpec.retrieve()).thenReturn(mockResponseSpec);
    when(mockResponseSpec.onStatus(any(), any())).thenReturn(mockResponseSpec);
    when(mockResponseSpec.body(any(ParameterizedTypeReference.class)))
        .thenReturn(
            Map.of(
                "access_token", "new-access-token",
                "refresh_token", "new-refresh-token",
                "expires_in", 1800));
  }

  private void mockTokenClientFailure() {
    var mockRequestBodyUriSpec =
        org.mockito.Mockito.mock(
            RestClient.RequestBodyUriSpec.class, org.mockito.Answers.RETURNS_SELF);
    var mockResponseSpec = org.mockito.Mockito.mock(RestClient.ResponseSpec.class);

    when(tokenClient.post()).thenReturn(mockRequestBodyUriSpec);
    when(mockRequestBodyUriSpec.retrieve()).thenReturn(mockResponseSpec);
    when(mockResponseSpec.onStatus(any(), any())).thenReturn(mockResponseSpec);
    when(mockResponseSpec.body(any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("Token endpoint unreachable"));
  }

  @Test
  void initiateConnect_generatesValidAuthorizationUrlWithPkce() {
    runInTenant(
        () -> {
          UUID memberId = UUID.randomUUID();
          var result = xeroOAuthService.initiateConnect(memberId);

          assertThat(result.authorizationUrl()).contains("response_type=code");
          assertThat(result.authorizationUrl()).contains("client_id=test-xero-client-id");
          assertThat(result.authorizationUrl()).contains("code_challenge=");
          assertThat(result.authorizationUrl()).contains("code_challenge_method=S256");
          assertThat(result.authorizationUrl()).contains("scope=");
          assertThat(result.state()).isNotBlank();

          // Verify the state -> verifier mapping was stored
          String stateKey = "xero:oauth:state:" + result.state();
          assertThat(secretStore.exists(stateKey)).isTrue();
          String verifier = secretStore.retrieve(stateKey);
          assertThat(verifier).isNotBlank();
          assertThat(verifier.length()).isGreaterThanOrEqualTo(43);
        });
  }

  @Test
  void handleCallback_invalidState_throwsInvalidStateException() {
    runInTenant(
        () -> {
          assertThatThrownBy(
                  () ->
                      xeroOAuthService.handleCallback(
                          "test-code", "invalid-state", UUID.randomUUID()))
              .isInstanceOf(InvalidStateException.class);
        });
  }

  @Test
  void refreshAccessToken_success_updatesTokensAndResetsFailureCount() {
    runInTenant(
        () -> {
          // Delete any existing connection for this integration (unique constraint)
          connectionRepository
              .findByOrgIntegrationId(sharedIntegrationId)
              .ifPresent(connectionRepository::delete);
          connectionRepository.flush();

          var connection =
              createConnection("refresh-ok-" + UUID.randomUUID().toString().substring(0, 4));

          String integrationId = sharedIntegrationId.toString();
          secretStore.store(integrationId + ":xero:access", "old-access-token");
          secretStore.store(integrationId + ":xero:refresh", "old-refresh-token");

          mockTokenClientSuccess();

          xeroOAuthService.refreshAccessToken(connection.getId());

          // Verify new tokens stored
          assertThat(secretStore.retrieve(integrationId + ":xero:access"))
              .isEqualTo("new-access-token");
          assertThat(secretStore.retrieve(integrationId + ":xero:refresh"))
              .isEqualTo("new-refresh-token");

          // Verify connection updated
          var reloaded = connectionRepository.findOneById(connection.getId()).orElseThrow();
          assertThat(reloaded.getStatus()).isEqualTo(XeroConnectionStatus.CONNECTED);
          assertThat(reloaded.getLastTokenRefreshAt()).isNotNull();
          assertThat(reloaded.getRefreshFailureCount()).isZero();
        });
  }

  @Test
  void refreshAccessToken_threeConsecutiveFailures_marksRefreshFailed() {
    runInTenant(
        () -> {
          // Delete any existing connection for this integration (unique constraint)
          connectionRepository
              .findByOrgIntegrationId(sharedIntegrationId)
              .ifPresent(connectionRepository::delete);
          connectionRepository.flush();

          var connection =
              createConnection("refresh-fail-" + UUID.randomUUID().toString().substring(0, 4));

          String integrationId = sharedIntegrationId.toString();
          secretStore.store(integrationId + ":xero:access", "access-token");
          secretStore.store(integrationId + ":xero:refresh", "refresh-token");

          mockTokenClientFailure();

          UUID connectionId = connection.getId();

          // Fail 3 times
          for (int i = 0; i < 3; i++) {
            try {
              xeroOAuthService.refreshAccessToken(connectionId);
            } catch (RuntimeException expected) {
              // expected
            }
          }

          // Verify connection is now REFRESH_FAILED
          var reloaded = connectionRepository.findOneById(connectionId).orElseThrow();
          assertThat(reloaded.getStatus()).isEqualTo(XeroConnectionStatus.REFRESH_FAILED);
          assertThat(reloaded.getRefreshFailureCount()).isGreaterThanOrEqualTo(3);
        });
  }

  @Test
  void disconnect_marksConnectionRevokedAndDeletesTokens() {
    runInTenant(
        () -> {
          // Delete any existing connection for this integration (unique constraint)
          connectionRepository
              .findByOrgIntegrationId(sharedIntegrationId)
              .ifPresent(connectionRepository::delete);
          connectionRepository.flush();

          // Re-enable the shared integration (may have been disabled by previous test)
          orgIntegrationRepository
              .findById(sharedIntegrationId)
              .ifPresent(
                  integration -> {
                    integration.enable();
                    orgIntegrationRepository.save(integration);
                  });

          var connection =
              createConnection("disconnect-" + UUID.randomUUID().toString().substring(0, 4));

          // Store tokens
          String integrationId = sharedIntegrationId.toString();
          secretStore.store(integrationId + ":xero:access", "test-access-token");
          secretStore.store(integrationId + ":xero:refresh", "test-refresh-token");

          UUID memberId = UUID.randomUUID();
          xeroOAuthService.disconnect(memberId);

          // Verify connection is REVOKED
          var reloaded = connectionRepository.findOneById(connection.getId()).orElseThrow();
          assertThat(reloaded.getStatus()).isEqualTo(XeroConnectionStatus.REVOKED);
          assertThat(reloaded.getDisconnectedAt()).isNotNull();

          // Verify tokens deleted
          assertThat(secretStore.exists(integrationId + ":xero:access")).isFalse();
          assertThat(secretStore.exists(integrationId + ":xero:refresh")).isFalse();

          // Verify integration disabled
          var reloadedIntegration =
              orgIntegrationRepository.findById(sharedIntegrationId).orElseThrow();
          assertThat(reloadedIntegration.isEnabled()).isFalse();
        });
  }
}

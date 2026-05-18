package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegration;
import io.b2mash.b2b.b2bstrawman.integration.OrgIntegrationRepository;
import io.b2mash.b2b.b2bstrawman.integration.accounting.AccountingTaxCodeMappingService;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Orchestrates the Xero OAuth2 authorization code flow with PKCE and manages the full token
 * lifecycle: initiate connect, handle callback, refresh tokens, and disconnect.
 */
@Service
public class XeroOAuthService {

  private static final Logger log = LoggerFactory.getLogger(XeroOAuthService.class);
  private static final int MAX_REFRESH_FAILURES = 3;
  private static final String XERO_SCOPES =
      "offline_access openid profile email accounting.transactions accounting.contacts";

  private final XeroProperties properties;
  private final SecretStore secretStore;
  private final OrgIntegrationRepository orgIntegrationRepository;
  private final AccountingXeroConnectionRepository connectionRepository;
  private final AccountingTaxCodeMappingService taxCodeMappingService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final XeroApiClient xeroApiClient;
  private final RestClient tokenClient;

  public XeroOAuthService(
      XeroProperties properties,
      SecretStore secretStore,
      OrgIntegrationRepository orgIntegrationRepository,
      AccountingXeroConnectionRepository connectionRepository,
      AccountingTaxCodeMappingService taxCodeMappingService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      XeroApiClient xeroApiClient,
      @Qualifier("xeroTokenClient") RestClient xeroTokenClient) {
    this.properties = properties;
    this.secretStore = secretStore;
    this.orgIntegrationRepository = orgIntegrationRepository;
    this.connectionRepository = connectionRepository;
    this.taxCodeMappingService = taxCodeMappingService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.xeroApiClient = xeroApiClient;
    this.tokenClient = xeroTokenClient;
  }

  /** Result of initiating a Xero connection — contains the authorization URL and state. */
  public record XeroConnectResult(String authorizationUrl, String state) {}

  /** Result of handling the OAuth callback — contains the connection ID and Xero org name. */
  public record XeroCallbackResult(UUID connectionId, String xeroOrgName) {}

  /**
   * Step 1: Initiates the OAuth2 authorization code flow with PKCE. Generates a code verifier and
   * challenge, stores the state-to-verifier mapping in SecretStore, and returns the authorization
   * URL for the user to visit.
   */
  public XeroConnectResult initiateConnect(UUID memberId) {
    log.info("Initiating Xero OAuth connect for member {}", memberId);

    String codeVerifier = generateCodeVerifier();
    String codeChallenge = computeCodeChallenge(codeVerifier);
    String state = UUID.randomUUID().toString();

    // Store state -> verifier mapping (temporary, consumed on callback)
    secretStore.store("xero:oauth:state:" + state, codeVerifier);

    String authorizationUrl =
        properties.authorizeUrl()
            + "?response_type=code"
            + "&client_id="
            + properties.clientId()
            + "&redirect_uri="
            + encodeUri(properties.redirectUri())
            + "&scope="
            + encodeUri(XERO_SCOPES)
            + "&state="
            + state
            + "&code_challenge="
            + codeChallenge
            + "&code_challenge_method=S256";

    return new XeroConnectResult(authorizationUrl, state);
  }

  /**
   * Step 2: Handles the OAuth2 callback. Validates the state, exchanges the authorization code for
   * tokens, fetches connections for the tenant ID, and creates the connection entity.
   */
  @Transactional
  @SuppressWarnings("unchecked")
  public XeroCallbackResult handleCallback(String code, String state, UUID memberId) {
    log.info("Handling Xero OAuth callback for member {}", memberId);

    // 1. Validate state and retrieve verifier
    String stateKey = "xero:oauth:state:" + state;
    if (!secretStore.exists(stateKey)) {
      throw new InvalidStateException(
          "Invalid OAuth state", "The OAuth state parameter is invalid or expired");
    }
    String codeVerifier = secretStore.retrieve(stateKey);
    secretStore.delete(stateKey); // One-time use

    // 2. Exchange code for tokens
    Map<String, Object> tokenResponse = exchangeCodeForTokens(code, codeVerifier);

    String accessToken = (String) tokenResponse.get("access_token");
    String refreshToken = (String) tokenResponse.get("refresh_token");
    int expiresIn = tokenResponse.get("expires_in") instanceof Number n ? n.intValue() : 1800;
    String grantedScope = tokenResponse.get("scope") instanceof String s ? s : XERO_SCOPES;

    // 3. Fetch Xero connections to get tenant ID and org name
    List<Map<String, Object>> connections = xeroApiClient.getConnections(accessToken);
    if (connections.isEmpty()) {
      throw new RuntimeException("No Xero tenants found after OAuth authorization");
    }
    Map<String, Object> firstConnection = connections.getFirst();
    String xeroTenantId = (String) firstConnection.get("tenantId");
    String xeroOrgName = (String) firstConnection.get("tenantName");

    // 4. Upsert OrgIntegration
    OrgIntegration integration =
        orgIntegrationRepository
            .findByDomain(IntegrationDomain.ACCOUNTING)
            .orElseGet(
                () -> {
                  var newIntegration = new OrgIntegration(IntegrationDomain.ACCOUNTING, "xero");
                  return orgIntegrationRepository.save(newIntegration);
                });
    integration.updateProvider("xero", null);
    integration.enable();
    orgIntegrationRepository.save(integration);

    // 5. Create AccountingXeroConnection
    var connection =
        new AccountingXeroConnection(
            integration.getId(),
            xeroTenantId,
            xeroOrgName,
            memberId,
            Instant.now().plusSeconds(expiresIn),
            grantedScope);
    connection = connectionRepository.save(connection);

    // 6. Store tokens in SecretStore
    String integrationId = integration.getId().toString();
    secretStore.store(integrationId + ":xero:access", accessToken);
    secretStore.store(integrationId + ":xero:refresh", refreshToken);

    // 7. Pre-seed tax mappings
    taxCodeMappingService.resetToDefaults("xero");

    // 8. Publish domain event
    eventPublisher.publishEvent(
        new XeroConnectionEstablishedEvent(connection.getId(), xeroOrgName, memberId));

    // 9. Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("integration.xero.connected")
            .entityType("org_integration")
            .entityId(integration.getId())
            .details(
                Map.of(
                    "xeroOrgName", xeroOrgName,
                    "xeroTenantId", xeroTenantId,
                    "memberId", memberId.toString()))
            .build());

    log.info(
        "Xero connection established: connectionId={}, xeroOrg={}",
        connection.getId(),
        xeroOrgName);

    return new XeroCallbackResult(connection.getId(), xeroOrgName);
  }

  /**
   * Step 3: Refreshes the access token using the stored refresh token. On success, stores new
   * tokens and resets the failure counter. After 3 consecutive failures, marks the connection as
   * REFRESH_FAILED.
   *
   * <p>Intentionally NOT {@code @Transactional} — on failure the catch block persists the
   * incremented failure counter and (on 3rd failure) the REFRESH_FAILED status, then re-throws. A
   * wrapping transaction would roll back that state change on the re-throw.
   */
  public void refreshAccessToken(UUID connectionId) {
    var connection =
        connectionRepository
            .findOneById(connectionId)
            .orElseThrow(
                () -> new ResourceNotFoundException("AccountingXeroConnection", connectionId));

    String integrationId = connection.getOrgIntegrationId().toString();
    String refreshToken = secretStore.retrieve(integrationId + ":xero:refresh");

    try {
      Map<String, Object> tokenResponse = refreshTokens(refreshToken);

      String newAccessToken = (String) tokenResponse.get("access_token");
      String newRefreshToken = (String) tokenResponse.get("refresh_token");
      int expiresIn = tokenResponse.get("expires_in") instanceof Number n ? n.intValue() : 1800;

      // Store new tokens
      secretStore.store(integrationId + ":xero:access", newAccessToken);
      if (newRefreshToken != null) {
        secretStore.store(integrationId + ":xero:refresh", newRefreshToken);
      }

      // Update connection timestamps and reset failure counter
      connection.recordTokenRefresh(Instant.now().plusSeconds(expiresIn));
      connectionRepository.save(connection);

      log.debug("Xero token refreshed for connection {}", connectionId);

    } catch (Exception e) {
      log.warn("Xero token refresh failed for connection {}: {}", connectionId, e.getMessage());

      int failures = connection.incrementRefreshFailureCount();
      if (failures >= MAX_REFRESH_FAILURES) {
        connection.markRefreshFailed();
        log.error(
            "Xero connection {} marked REFRESH_FAILED after {} consecutive failures",
            connectionId,
            failures);

        auditService.log(
            AuditEventBuilder.builder()
                .eventType("integration.xero.refresh_failed")
                .entityType("accounting_xero_connection")
                .entityId(connectionId)
                .details(
                    Map.of(
                        "consecutiveFailures",
                        failures,
                        "error",
                        e.getMessage() != null ? e.getMessage() : "unknown"))
                .build());
      }
      connectionRepository.save(connection);

      throw new RuntimeException("Xero token refresh failed for connection " + connectionId, e);
    }
  }

  /**
   * Step 4: Disconnects from Xero. Revokes the refresh token (best-effort), deletes stored tokens,
   * marks the connection as REVOKED, and disables the OrgIntegration.
   */
  @Transactional
  public void disconnect(UUID memberId) {
    log.info("Disconnecting Xero for member {}", memberId);

    // Find a non-REVOKED connection
    var connection =
        connectionRepository.findByStatus(XeroConnectionStatus.CONNECTED).stream()
            .findFirst()
            .or(
                () ->
                    connectionRepository.findByStatus(XeroConnectionStatus.REFRESH_FAILED).stream()
                        .findFirst())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "AccountingXeroConnection", "No active Xero connection found"));

    String integrationId = connection.getOrgIntegrationId().toString();

    // 1. Best-effort token revocation at Xero
    try {
      if (secretStore.exists(integrationId + ":xero:refresh")) {
        String refreshToken = secretStore.retrieve(integrationId + ":xero:refresh");
        revokeToken(refreshToken);
      }
    } catch (Exception e) {
      log.warn("Best-effort Xero token revocation failed: {}", e.getMessage());
    }

    // 2. Delete tokens from SecretStore
    secretStore.delete(integrationId + ":xero:access");
    secretStore.delete(integrationId + ":xero:refresh");

    // 3. Mark connection revoked
    connection.markRevoked();
    connectionRepository.save(connection);

    // 4. Disable OrgIntegration
    orgIntegrationRepository
        .findById(connection.getOrgIntegrationId())
        .ifPresent(
            integration -> {
              integration.disable();
              orgIntegrationRepository.save(integration);
            });

    // 5. Publish domain event
    eventPublisher.publishEvent(
        new XeroConnectionRevokedEvent(connection.getId(), connection.getXeroOrgName(), memberId));

    // 6. Audit
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("integration.xero.disconnected")
            .entityType("accounting_xero_connection")
            .entityId(connection.getId())
            .details(
                Map.of(
                    "xeroOrgName", connection.getXeroOrgName(),
                    "memberId", memberId.toString()))
            .build());

    log.info("Xero connection {} disconnected", connection.getId());
  }

  // ---- Token endpoint calls ----

  @SuppressWarnings("unchecked")
  private Map<String, Object> exchangeCodeForTokens(String code, String codeVerifier) {
    var formData = new LinkedMultiValueMap<String, String>();
    formData.add("grant_type", "authorization_code");
    formData.add("code", code);
    formData.add("redirect_uri", properties.redirectUri());
    formData.add("code_verifier", codeVerifier);
    formData.add("client_id", properties.clientId());
    formData.add("client_secret", properties.clientSecret());

    return tokenClient
        .post()
        .uri(properties.tokenUrl())
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(formData)
        .retrieve()
        .onStatus(
            HttpStatusCode::isError,
            (req, resp) -> {
              throw new RuntimeException(
                  "Xero token exchange failed: HTTP " + resp.getStatusCode().value());
            })
        .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> refreshTokens(String refreshToken) {
    var formData = new LinkedMultiValueMap<String, String>();
    formData.add("grant_type", "refresh_token");
    formData.add("refresh_token", refreshToken);
    formData.add("client_id", properties.clientId());
    formData.add("client_secret", properties.clientSecret());

    return tokenClient
        .post()
        .uri(properties.tokenUrl())
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(formData)
        .retrieve()
        .onStatus(
            HttpStatusCode::isError,
            (req, resp) -> {
              throw new RuntimeException(
                  "Xero token refresh failed: HTTP " + resp.getStatusCode().value());
            })
        .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private void revokeToken(String token) {
    var formData = new LinkedMultiValueMap<String, String>();
    formData.add("token", token);
    formData.add("token_type_hint", "refresh_token");

    tokenClient
        .post()
        .uri(properties.revocationUrl())
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(formData)
        .retrieve()
        .toBodilessEntity();
  }

  // ---- PKCE helpers ----

  private String generateCodeVerifier() {
    var random = new SecureRandom();
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String computeCodeChallenge(String codeVerifier) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 not available", e);
    }
  }

  private String encodeUri(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}

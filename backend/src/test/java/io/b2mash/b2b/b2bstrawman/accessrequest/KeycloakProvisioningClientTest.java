package io.b2mash.b2b.b2bstrawman.accessrequest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link KeycloakProvisioningClient}, exercising the 409-idempotency retry path
 * (GAP-D0-01).
 *
 * <p>Keycloak's {@code GET /organizations?search=<alias>} query matches on organization
 * <b>name</b>, not alias, so when the retry path fires we must fetch candidates and filter
 * client-side on the {@code alias} field. These tests stub a WireMock Keycloak and verify the
 * client:
 *
 * <ul>
 *   <li>finds the right org when multiple candidates have similar names but different aliases,
 *   <li>falls back to a list-all request when the narrow search returns nothing useful,
 *   <li>throws {@link IllegalStateException} only when no match exists anywhere.
 * </ul>
 */
class KeycloakProvisioningClientTest {

  private static WireMockServer wireMock;
  private static String originalTransformerFactory;
  private KeycloakProvisioningClient client;

  @BeforeAll
  static void startWireMock() {
    // Force JDK default TransformerFactory to avoid docx4j's impl which doesn't support
    // indent-number (causes WireMock's FormatXmlHelper initialization to fail).
    originalTransformerFactory = System.getProperty("javax.xml.transform.TransformerFactory");
    System.setProperty(
        "javax.xml.transform.TransformerFactory",
        "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMock != null) {
      wireMock.stop();
    }
    if (originalTransformerFactory != null) {
      System.setProperty("javax.xml.transform.TransformerFactory", originalTransformerFactory);
    } else {
      System.clearProperty("javax.xml.transform.TransformerFactory");
    }
  }

  @BeforeEach
  void setUp() {
    wireMock.resetAll();
    stubAdminToken();
    client =
        new KeycloakProvisioningClient(
            "http://localhost:" + wireMock.port(),
            "docteams",
            "admin",
            "admin",
            "http://localhost:3000");
  }

  // ---------------------------------------------------------------------------
  // 409 idempotency retry — alias lookup (GAP-D0-01)
  // ---------------------------------------------------------------------------

  @Test
  void createOrganization_on409_returnsExistingIdByAliasMatch_notByName() {
    // POST /organizations → 409 (org already exists)
    wireMock.stubFor(
        post(urlEqualTo("/admin/realms/docteams/organizations"))
            .willReturn(aResponse().withStatus(409).withBody("{\"error\":\"conflict\"}")));

    // KC's search param matches on name — it returns two orgs whose names both contain
    // "mathebula" but with different aliases. The client MUST pick the one whose alias
    // exactly matches, not just the first result.
    wireMock.stubFor(
        get(urlPathEqualTo("/admin/realms/docteams/organizations"))
            .withQueryParam("search", containing("mathebula-partners"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        [
                          {"id":"org-wrong-1","name":"Mathebula Holdings","alias":"mathebula-holdings"},
                          {"id":"org-correct","name":"Mathebula & Partners","alias":"mathebula-partners"},
                          {"id":"org-wrong-2","name":"Mathebula Legal","alias":"mathebula-legal"}
                        ]
                        """)));

    String id = client.createOrganization("Mathebula & Partners", "mathebula-partners");

    assertThat(id).isEqualTo("org-correct");
  }

  @Test
  void createOrganization_on409_fallsBackToListAll_whenSearchReturnsNoAliasMatch() {
    // POST /organizations → 409
    wireMock.stubFor(
        post(urlEqualTo("/admin/realms/docteams/organizations"))
            .willReturn(aResponse().withStatus(409).withBody("{\"error\":\"conflict\"}")));

    // The narrow search by alias returns an org with a similar name but a different alias
    // (mimicking the real-world case where `search` matches the name field). No alias match.
    wireMock.stubFor(
        get(urlPathEqualTo("/admin/realms/docteams/organizations"))
            .withQueryParam("search", containing("mathebula-partners"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        [
                          {"id":"org-name-collision","name":"Some Mathebula-Partners LLC","alias":"different-alias"}
                        ]
                        """)));

    // Fallback: list all orgs — the correct one is in here.
    wireMock.stubFor(
        get(urlPathEqualTo("/admin/realms/docteams/organizations"))
            .withQueryParam("first", containing("0"))
            .withQueryParam("max", containing("200"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        [
                          {"id":"org-name-collision","name":"Some Mathebula-Partners LLC","alias":"different-alias"},
                          {"id":"org-correct","name":"Mathebula & Partners","alias":"mathebula-partners"}
                        ]
                        """)));

    String id = client.createOrganization("Mathebula & Partners", "mathebula-partners");

    assertThat(id).isEqualTo("org-correct");
  }

  @Test
  void createOrganization_on409_throwsWhenAliasNotFoundAnywhere() {
    wireMock.stubFor(
        post(urlEqualTo("/admin/realms/docteams/organizations"))
            .willReturn(aResponse().withStatus(409).withBody("{\"error\":\"conflict\"}")));

    // Narrow search: empty
    wireMock.stubFor(
        get(urlPathEqualTo("/admin/realms/docteams/organizations"))
            .withQueryParam("search", containing("ghost-alias"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]")));

    // Fallback list-all: also no match
    wireMock.stubFor(
        get(urlPathEqualTo("/admin/realms/docteams/organizations"))
            .withQueryParam("first", containing("0"))
            .withQueryParam("max", containing("200"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        [
                          {"id":"org-a","name":"Alpha","alias":"alpha"},
                          {"id":"org-b","name":"Beta","alias":"beta"}
                        ]
                        """)));

    assertThatThrownBy(() -> client.createOrganization("Ghost Org", "ghost-alias"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ghost-alias");
  }

  @Test
  void createOrganization_happyPath_returnsIdFromLocationHeader() {
    wireMock.stubFor(
        post(urlEqualTo("/admin/realms/docteams/organizations"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader(
                        "Location",
                        "http://localhost/admin/realms/docteams/organizations/new-org-id-123")));

    String id = client.createOrganization("Acme Inc", "acme-inc");

    assertThat(id).isEqualTo("new-org-id-123");
  }

  // ---------------------------------------------------------------------------
  // GAP-L-22 regression — KC org redirectUrl must target the bounce page so the
  // post-registration callback re-enters the gateway-bff OAuth2 flow. See
  // qa_cycle/fix-specs/GAP-L-22-regression.md.
  // ---------------------------------------------------------------------------

  @Test
  void createOrganization_postsRedirectUrlPointingAtAcceptInviteCompleteBouncePage() {
    wireMock.stubFor(
        post(urlEqualTo("/admin/realms/docteams/organizations"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader(
                        "Location",
                        "http://localhost/admin/realms/docteams/organizations/new-org-id-456")));

    client.createOrganization("Mathebula & Partners", "mathebula-partners");

    wireMock.verify(
        postRequestedFor(urlEqualTo("/admin/realms/docteams/organizations"))
            .withRequestBody(
                containing("\"redirectUrl\":\"http://localhost:3000/accept-invite/complete\"")));
  }

  @Test
  void createOrganization_redirectUrlStripsTrailingSlashOnFrontendBaseUrl() {
    // Re-create the client with a trailing-slash frontend base URL to ensure normalisation.
    client =
        new KeycloakProvisioningClient(
            "http://localhost:" + wireMock.port(),
            "docteams",
            "admin",
            "admin",
            "http://localhost:3000//");

    wireMock.stubFor(
        post(urlEqualTo("/admin/realms/docteams/organizations"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader(
                        "Location",
                        "http://localhost/admin/realms/docteams/organizations/new-org-id-789")));

    client.createOrganization("Acme Inc", "acme-inc");

    wireMock.verify(
        postRequestedFor(urlEqualTo("/admin/realms/docteams/organizations"))
            .withRequestBody(
                containing("\"redirectUrl\":\"http://localhost:3000/accept-invite/complete\"")));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static void stubAdminToken() {
    wireMock.stubFor(
        post(urlEqualTo("/realms/master/protocol/openid-connect/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"access_token\":\"fake-admin-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
  }
}

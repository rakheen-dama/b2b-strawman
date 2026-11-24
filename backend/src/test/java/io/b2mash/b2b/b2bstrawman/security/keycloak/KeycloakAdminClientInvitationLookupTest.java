package io.b2mash.b2b.b2bstrawman.security.keycloak;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FR-8 review finding: the revoke must find the Keycloak invitation for an email exhaustively — the
 * {@code email} filter of {@code GET /organizations/{id}/invitations} (Keycloak 26.5) is a LIKE,
 * and results are paged, so the client pages until a short page and re-checks the email exactly.
 */
class KeycloakAdminClientInvitationLookupTest {

  private static final String INVITATIONS = "/admin/realms/kazi/organizations/kc-org/invitations";
  private static WireMockServer wireMock;
  private static String originalTransformerFactory;
  private KeycloakAdminClient client;

  @BeforeAll
  static void startWireMock() {
    // the JDK TransformerFactory (docx4j's breaks WireMock's FormatXmlHelper initialisation)
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
    wireMock.stubFor(
        post(urlEqualTo("/realms/master/protocol/openid-connect/token"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"access_token\":\"t\",\"expires_in\":300}")));
    client =
        new KeycloakAdminClient(
            new KeycloakAdminConfig("http://localhost:" + wireMock.port(), "kazi", "a", "b"));
  }

  @Test
  void findsTheInvitationOnALaterPage_andIgnoresLikeMatches() {
    // page 1: a full page of LIKE matches for "ann@firm.co.za" that are other addresses
    String page1 =
        IntStream.range(0, 100)
            .mapToObj(i -> "{\"id\":\"other-%d\",\"email\":\"x%dann@firm.co.za\"}".formatted(i, i))
            .collect(Collectors.joining(",", "[", "]"));
    stubPage(0, page1);
    stubPage(100, "[{\"id\":\"kc-inv-target\",\"email\":\"ann@firm.co.za\"}]");

    assertThat(client.findInvitationIdsByEmail("kc-org", "ann@firm.co.za"))
        .containsExactly("kc-inv-target");
    wireMock.verify(
        getRequestedFor(urlPathEqualTo(INVITATIONS))
            .withQueryParam("email", equalTo("ann@firm.co.za"))
            .withQueryParam("first", equalTo("100"))
            .withQueryParam("max", equalTo("100")));
  }

  @Test
  void noInvitationForThatEmail_isAnEmptyList() {
    stubPage(0, "[{\"id\":\"other\",\"email\":\"joann@firm.co.za\"}]");

    assertThat(client.findInvitationIdsByEmail("kc-org", "ann@firm.co.za")).isEmpty();
  }

  @Test
  void aKeycloakErrorDuringTheLookupThrows() {
    wireMock.stubFor(
        get(urlPathEqualTo(INVITATIONS)).willReturn(aResponse().withStatus(503).withBody("down")));

    assertThatThrownBy(() -> client.findInvitationIdsByEmail("kc-org", "ann@firm.co.za"))
        .isInstanceOf(InvalidStateException.class);
  }

  private static void stubPage(int first, String body) {
    wireMock.stubFor(
        get(urlPathEqualTo(INVITATIONS))
            .withQueryParam("email", equalTo("ann@firm.co.za"))
            .withQueryParam("first", equalTo(String.valueOf(first)))
            .withQueryParam("max", equalTo("100"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(body)));
  }
}

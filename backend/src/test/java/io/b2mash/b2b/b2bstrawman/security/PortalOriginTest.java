package io.b2mash.b2b.b2bstrawman.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PortalOriginTest {

  // ── valid ─────────────────────────────────────────────────────────────

  @Test
  void parse_validOrigin_returnsSchemeHostPort() {
    assertThat(new PortalOrigin("http://localhost:3002").value())
        .isEqualTo("http://localhost:3002");
  }

  @Test
  void parse_withPathQueryAndFragment_stripsThem() {
    assertThat(PortalOrigin.parse("https://portal-dev.heykazi.com/some/path?x=1#f"))
        .isEqualTo("https://portal-dev.heykazi.com");
    assertThat(PortalOrigin.parse("http://localhost:3002/")).isEqualTo("http://localhost:3002");
  }

  @Test
  void parse_upperCaseSchemeAndHost_lowerCasesAndKeepsPort() {
    assertThat(PortalOrigin.parse("HTTPS://Portal.Example.COM:8443/p"))
        .isEqualTo("https://portal.example.com:8443");
  }

  @Test
  void parse_ipv4Host_isAccepted() {
    assertThat(PortalOrigin.parse("http://127.0.0.1:3002")).isEqualTo("http://127.0.0.1:3002");
  }

  // ── malformed ─────────────────────────────────────────────────────────

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "not a url",
        "localhost:3002",
        "http://",
        "ftp://x.com",
        "http://a;b.com",
        "https://user@x.com",
        "http://[::1]:3002",
        "http://example.com.:3002",
        "/relative/path"
      })
  void parse_malformed_throwsIllegalStateException(String raw) {
    assertThatThrownBy(() -> new PortalOrigin(raw)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void parse_null_throwsIllegalStateException() {
    assertThatThrownBy(() -> PortalOrigin.parse(null)).isInstanceOf(IllegalStateException.class);
  }
}

package io.b2mash.b2b.b2bstrawman.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PortalLinkBuilderTest {

  private final PortalLinkBuilder links = new PortalLinkBuilder("https://portal.example.com");

  @Test
  void authenticated_appendsOrgIdWithQuestionMark() {
    assertThat(links.authenticated("mathebula-partners", "/invoices/abc"))
        .isEqualTo("https://portal.example.com/invoices/abc?orgId=mathebula-partners");
  }

  @Test
  void authenticated_appendsOrgIdWithAmpersandWhenPathHasQuery() {
    assertThat(links.authenticated("acme", "/settings/notifications?unsubscribe=1"))
        .isEqualTo("https://portal.example.com/settings/notifications?unsubscribe=1&orgId=acme");
  }

  @Test
  void exchange_keepsTokenThenOrgIdOrder() {
    assertThat(links.exchange("acme", "tok_-123"))
        .isEqualTo("https://portal.example.com/auth/exchange?token=tok_-123&orgId=acme");
  }

  @Test
  void acceptance_isPublicTokenPathWithoutOrg() {
    assertThat(links.acceptance("abc-_DEF"))
        .isEqualTo("https://portal.example.com/accept/abc-_DEF");
  }

  @Test
  void trailingSlashInBaseIsNormalised() {
    assertThat(new PortalLinkBuilder("http://localhost:3002/").authenticated("o", "/home"))
        .isEqualTo("http://localhost:3002/home?orgId=o");
  }

  @Test
  void blankOrgIdIsRejected() {
    assertThatThrownBy(() -> links.authenticated(" ", "/home"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> links.exchange(null, "t"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void queryValuesAreEncoded() {
    assertThat(links.authenticated("a&b c", "/home"))
        .isEqualTo("https://portal.example.com/home?orgId=a%26b%20c");
  }

  @Test
  void pathMustBeAbsolute() {
    assertThatThrownBy(() -> links.authenticated("o", "home"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

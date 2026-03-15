package io.b2mash.b2b.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BFF endpoint that exposes the current user's identity and organization info from the session. */
@RestController
@RequestMapping("/bff")
public class BffController {

  private static final Logger log = LoggerFactory.getLogger(BffController.class);

  private final CookieCsrfTokenRepository csrfTokenRepository;

  public BffController(CookieCsrfTokenRepository csrfTokenRepository) {
    this.csrfTokenRepository = csrfTokenRepository;
  }

  /** Response DTO for the /bff/me endpoint. */
  public record BffUserInfo(
      boolean authenticated,
      String userId,
      String email,
      String name,
      String picture,
      String orgId,
      String orgSlug,
      List<String> groups) {

    /** Factory for unauthenticated response. */
    public static BffUserInfo unauthenticated() {
      return new BffUserInfo(false, null, null, null, null, null, null, List.of());
    }
  }

  /**
   * Returns the current CSRF token so the SPA can perform form POSTs (e.g., logout).
   *
   * <p>Loads the raw token directly from the {@link CookieCsrfTokenRepository} instead of the
   * request attribute, which is XOR-masked by {@code SpaCsrfTokenRequestHandler}. Form submissions
   * use the plain CSRF resolver (no header), so the submitted value must match the raw cookie
   * value. XOR-masked tokens only work when sent via the {@code X-XSRF-TOKEN} header.
   */
  @GetMapping("/csrf")
  public ResponseEntity<Map<String, String>> csrf(
      HttpServletRequest request, HttpServletResponse response) {
    CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
    if (csrfToken == null) {
      csrfToken = csrfTokenRepository.generateToken(request);
      csrfTokenRepository.saveToken(csrfToken, request, response);
    }
    return ResponseEntity.ok(
        Map.of(
            "token", csrfToken.getToken(),
            "parameterName", csrfToken.getParameterName(),
            "headerName", csrfToken.getHeaderName()));
  }

  @GetMapping("/me")
  public ResponseEntity<BffUserInfo> me(@AuthenticationPrincipal OidcUser user) {
    if (user == null) {
      return ResponseEntity.ok(BffUserInfo.unauthenticated());
    }

    log.info("BFF /me claims: {}", user.getClaims());
    BffUserInfoExtractor.OrgInfo orgInfo = BffUserInfoExtractor.extractOrgInfo(user);
    List<String> groups = extractGroups(user);

    return ResponseEntity.ok(
        new BffUserInfo(
            true,
            user.getSubject(),
            user.getEmail(),
            user.getFullName(),
            Objects.toString(user.getPicture(), ""),
            orgInfo != null ? orgInfo.id() : null,
            orgInfo != null ? orgInfo.slug() : null,
            groups));
  }

  /** Extracts the groups claim from the OidcUser, defaulting to an empty list. */
  private static List<String> extractGroups(OidcUser user) {
    Object raw = user.getClaim("groups");
    if (raw instanceof List<?> list) {
      return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
    return List.of();
  }
}

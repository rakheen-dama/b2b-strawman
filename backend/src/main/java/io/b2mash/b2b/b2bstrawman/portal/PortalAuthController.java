package io.b2mash.b2b.b2bstrawman.portal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal authentication endpoints. These are unauthenticated (permitted by the portal filter chain)
 * and allow customers to request magic links and exchange them for portal JWTs.
 */
@RestController
@RequestMapping("/portal/auth")
public class PortalAuthController {

  private final PortalAuthService portalAuthService;

  public PortalAuthController(PortalAuthService portalAuthService) {
    this.portalAuthService = portalAuthService;
  }

  @PostMapping("/request-link")
  public ResponseEntity<MagicLinkResponse> requestMagicLink(
      @Valid @RequestBody MagicLinkRequest request, HttpServletRequest httpRequest) {
    var result =
        portalAuthService.requestMagicLink(
            request.email(), request.orgId(), httpRequest.getRemoteAddr());
    return ResponseEntity.ok(new MagicLinkResponse(result.message(), result.magicLink()));
  }

  @PostMapping("/exchange")
  public ResponseEntity<PortalTokenResponse> exchangeToken(
      @Valid @RequestBody ExchangeRequest request) {
    var result = portalAuthService.exchangeToken(request.token(), request.orgId());
    return ResponseEntity.ok(
        new PortalTokenResponse(result.token(), result.customerId(), result.customerName()));
  }

  // --- DTOs ---

  public record MagicLinkRequest(
      @NotBlank(message = "email is required") @Email(message = "invalid email format")
          String email,
      @NotBlank(message = "orgId is required") String orgId) {}

  public record MagicLinkResponse(String message, String magicLink) {}

  public record ExchangeRequest(
      @NotBlank(message = "token is required") String token,
      @NotBlank(message = "orgId is required") String orgId) {}

  public record PortalTokenResponse(String token, UUID customerId, String customerName) {}
}

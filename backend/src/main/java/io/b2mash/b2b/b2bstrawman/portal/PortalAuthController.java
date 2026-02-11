package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(PortalAuthController.class);

  private final MagicLinkService magicLinkService;
  private final PortalJwtService portalJwtService;
  private final CustomerRepository customerRepository;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;

  public PortalAuthController(
      MagicLinkService magicLinkService,
      PortalJwtService portalJwtService,
      CustomerRepository customerRepository,
      OrgSchemaMappingRepository orgSchemaMappingRepository) {
    this.magicLinkService = magicLinkService;
    this.portalJwtService = portalJwtService;
    this.customerRepository = customerRepository;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
  }

  /**
   * Request a magic link for portal access. Resolves the org, looks up the customer by email, and
   * generates a magic link token. For MVP, the token is returned directly in the response (no email
   * sending).
   */
  @PostMapping("/request-link")
  public ResponseEntity<MagicLinkResponse> requestMagicLink(
      @Valid @RequestBody MagicLinkRequest request) {
    // Resolve org to verify it exists and get org ID
    var mapping =
        orgSchemaMappingRepository
            .findByClerkOrgId(request.orgId())
            .orElseThrow(() -> new PortalAuthException("Organization not found"));

    String tenantSchema = mapping.getSchemaName();

    // Look up customer by email within the tenant
    UUID customerId =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () ->
                    customerRepository
                        .findByEmail(request.email())
                        .orElseThrow(
                            () ->
                                new PortalAuthException("No customer account found for this email"))
                        .getId());

    String token = magicLinkService.generateToken(customerId, request.orgId());

    log.info("Generated magic link for customer {} in org {}", customerId, request.orgId());
    return ResponseEntity.ok(new MagicLinkResponse(token));
  }

  /**
   * Exchange a magic link token for a portal JWT. Verifies the magic link, validates the customer
   * still exists, and issues a session JWT.
   */
  @PostMapping("/exchange")
  public ResponseEntity<PortalTokenResponse> exchangeToken(
      @Valid @RequestBody ExchangeRequest request) {
    // Verify the magic link token
    var identity = magicLinkService.verifyToken(request.token());

    // Resolve org schema to verify tenant still exists
    var mapping =
        orgSchemaMappingRepository
            .findByClerkOrgId(identity.clerkOrgId())
            .orElseThrow(() -> new PortalAuthException("Organization not found"));

    // Validate customer still exists in tenant
    ScopedValue.where(RequestScopes.TENANT_ID, mapping.getSchemaName())
        .run(
            () ->
                customerRepository
                    .findOneById(identity.customerId())
                    .orElseThrow(
                        () -> new PortalAuthException("Customer account no longer exists")));

    // Issue portal JWT
    String portalToken = portalJwtService.issueToken(identity.customerId(), identity.clerkOrgId());

    log.info(
        "Exchanged magic link for portal JWT — customer {} in org {}",
        identity.customerId(),
        identity.clerkOrgId());
    return ResponseEntity.ok(new PortalTokenResponse(portalToken, identity.customerId()));
  }

  // --- DTOs ---

  public record MagicLinkRequest(
      @NotBlank(message = "email is required") @Email(message = "invalid email format")
          String email,
      @NotBlank(message = "orgId is required") String orgId) {}

  public record MagicLinkResponse(String token) {}

  public record ExchangeRequest(@NotBlank(message = "token is required") String token) {}

  public record PortalTokenResponse(String token, UUID customerId) {}
}

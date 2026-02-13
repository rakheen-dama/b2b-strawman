package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
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
  private static final String GENERIC_MESSAGE = "If an account exists, a link has been sent.";

  private final MagicLinkService magicLinkService;
  private final PortalJwtService portalJwtService;
  private final PortalContactRepository portalContactRepository;
  private final CustomerRepository customerRepository;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final Environment environment;

  public PortalAuthController(
      MagicLinkService magicLinkService,
      PortalJwtService portalJwtService,
      PortalContactRepository portalContactRepository,
      CustomerRepository customerRepository,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      Environment environment) {
    this.magicLinkService = magicLinkService;
    this.portalJwtService = portalJwtService;
    this.portalContactRepository = portalContactRepository;
    this.customerRepository = customerRepository;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.environment = environment;
  }

  /**
   * Request a magic link for portal access. Resolves the org, looks up the portal contact by email
   * and org, checks contact status, and generates a magic link token. Returns a generic message to
   * prevent email enumeration; in dev/test profiles, also returns the magic link URL.
   */
  @PostMapping("/request-link")
  public ResponseEntity<MagicLinkResponse> requestMagicLink(
      @Valid @RequestBody MagicLinkRequest request, HttpServletRequest httpRequest) {
    // Resolve org to verify it exists and get schema
    var mapping = orgSchemaMappingRepository.findByClerkOrgId(request.orgId()).orElse(null);

    // Return generic message even if org not found (prevent enumeration)
    if (mapping == null) {
      return ResponseEntity.ok(new MagicLinkResponse(GENERIC_MESSAGE, null));
    }

    String tenantSchema = mapping.getSchemaName();

    // Look up portal contact and generate token within the tenant scope
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .call(
              () -> {
                PortalContact contact =
                    portalContactRepository
                        .findByEmailAndOrgId(request.email(), request.orgId())
                        .orElse(null);

                // Return generic message if contact not found (prevent email enumeration)
                if (contact == null) {
                  return ResponseEntity.ok(new MagicLinkResponse(GENERIC_MESSAGE, null));
                }

                // Check contact status -- return generic message to prevent enumeration
                if (contact.getStatus() == PortalContact.ContactStatus.SUSPENDED
                    || contact.getStatus() == PortalContact.ContactStatus.ARCHIVED) {
                  log.info(
                      "Magic link requested for {} contact {}",
                      contact.getStatus(),
                      contact.getId());
                  return ResponseEntity.ok(new MagicLinkResponse(GENERIC_MESSAGE, null));
                }

                String rawToken =
                    magicLinkService.generateToken(contact.getId(), httpRequest.getRemoteAddr());

                log.info(
                    "Generated magic link for portal contact {} in org {}",
                    contact.getId(),
                    request.orgId());

                // In dev/test profiles, include the magic link URL for testing
                String magicLink = null;
                if (isDevProfile()) {
                  magicLink = "/portal/login?token=" + rawToken + "&orgId=" + request.orgId();
                }

                return ResponseEntity.ok(new MagicLinkResponse(GENERIC_MESSAGE, magicLink));
              });
    } catch (PortalAuthException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error processing magic link request", e);
      return ResponseEntity.ok(new MagicLinkResponse(GENERIC_MESSAGE, null));
    }
  }

  /**
   * Exchange a magic link token for a portal JWT. Verifies the magic link, loads the portal
   * contact, validates the customer is still active, and issues a session JWT.
   */
  @PostMapping("/exchange")
  public ResponseEntity<PortalTokenResponse> exchangeToken(
      @Valid @RequestBody ExchangeRequest request) {
    // Resolve org schema
    var mapping =
        orgSchemaMappingRepository
            .findByClerkOrgId(request.orgId())
            .orElseThrow(() -> new PortalAuthException("Organization not found"));

    String tenantSchema = mapping.getSchemaName();

    // Verify and consume the magic link token within tenant context
    UUID portalContactId;
    try {
      portalContactId =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
              .call(() -> magicLinkService.verifyAndConsumeToken(request.token()));
    } catch (PortalAuthException e) {
      throw e;
    } catch (Exception e) {
      throw new PortalAuthException("Token verification failed");
    }

    // Load portal contact and customer within tenant context
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .call(
              () -> {
                PortalContact contact =
                    portalContactRepository
                        .findOneById(portalContactId)
                        .orElseThrow(
                            () -> new PortalAuthException("Portal contact no longer exists"));

                // Verify contact belongs to the requesting org (cross-tenant isolation)
                if (!contact.getOrgId().equals(request.orgId())) {
                  throw new PortalAuthException("Invalid magic link token");
                }

                // Verify customer still active
                var customer =
                    customerRepository
                        .findOneById(contact.getCustomerId())
                        .orElseThrow(
                            () -> new PortalAuthException("Customer account no longer exists"));

                if (!"ACTIVE".equals(customer.getStatus())) {
                  throw new PortalAuthException("Customer account is no longer active");
                }

                // Issue portal JWT
                String portalToken =
                    portalJwtService.issueToken(contact.getCustomerId(), request.orgId());

                log.info(
                    "Exchanged magic link for portal JWT -- contact {} customer {} in org {}",
                    portalContactId,
                    contact.getCustomerId(),
                    request.orgId());

                return ResponseEntity.ok(
                    new PortalTokenResponse(
                        portalToken, contact.getCustomerId(), customer.getName()));
              });
    } catch (PortalAuthException e) {
      throw e;
    } catch (Exception e) {
      throw new PortalAuthException("Token exchange failed");
    }
  }

  private boolean isDevProfile() {
    for (String profile : environment.getActiveProfiles()) {
      if ("local".equals(profile) || "test".equals(profile) || "dev".equals(profile)) {
        return true;
      }
    }
    return false;
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

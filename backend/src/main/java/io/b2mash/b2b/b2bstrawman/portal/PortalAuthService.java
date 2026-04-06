package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Service encapsulating portal authentication orchestration: magic link requests and token
 * exchanges. Extracted from PortalAuthController to keep the controller thin and testable.
 */
@Service
public class PortalAuthService {

  private static final Logger log = LoggerFactory.getLogger(PortalAuthService.class);
  private static final String GENERIC_MESSAGE = "If an account exists, a link has been sent.";

  private final MagicLinkService magicLinkService;
  private final PortalJwtService portalJwtService;
  private final PortalContactRepository portalContactRepository;
  private final CustomerRepository customerRepository;
  private final OrgSchemaMappingRepository orgSchemaMappingRepository;
  private final Environment environment;

  public PortalAuthService(
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
   * Requests a magic link for portal access. Resolves the org, looks up the portal contact by
   * email, checks contact status, and generates a magic link token. Returns a generic message to
   * prevent email enumeration; in dev/test profiles, also returns the magic link URL.
   */
  public MagicLinkResult requestMagicLink(String email, String orgClerkId, String remoteAddr) {
    // Resolve org to verify it exists and get schema
    var mapping = orgSchemaMappingRepository.findByClerkOrgId(orgClerkId).orElse(null);

    // Return generic message even if org not found (prevent enumeration)
    if (mapping == null) {
      return new MagicLinkResult(GENERIC_MESSAGE, null);
    }

    String tenantSchema = mapping.getSchemaName();

    // Look up portal contact and generate token within the tenant scope
    try {
      return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
          .call(
              () -> {
                PortalContact contact =
                    portalContactRepository.findByEmailAndOrgId(email, orgClerkId).orElse(null);

                // Return generic message if contact not found (prevent email enumeration)
                if (contact == null) {
                  return new MagicLinkResult(GENERIC_MESSAGE, null);
                }

                // Check contact status -- return generic message to prevent enumeration
                if (contact.getStatus() == PortalContact.ContactStatus.SUSPENDED
                    || contact.getStatus() == PortalContact.ContactStatus.ARCHIVED) {
                  log.info(
                      "Magic link requested for {} contact {}",
                      contact.getStatus(),
                      contact.getId());
                  return new MagicLinkResult(GENERIC_MESSAGE, null);
                }

                String rawToken = magicLinkService.generateToken(contact.getId(), remoteAddr);

                log.info(
                    "Generated magic link for portal contact {} in org {}",
                    contact.getId(),
                    orgClerkId);

                // In dev/test profiles, include the magic link URL for testing
                String magicLink = null;
                if (isDevProfile()) {
                  magicLink = "/auth/exchange?token=" + rawToken + "&orgId=" + orgClerkId;
                }

                return new MagicLinkResult(GENERIC_MESSAGE, magicLink);
              });
    } catch (PortalAuthException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error processing magic link request", e);
      return new MagicLinkResult(GENERIC_MESSAGE, null);
    }
  }

  /**
   * Exchanges a magic link token for a portal JWT. Verifies the magic link, loads the portal
   * contact, validates the customer is still active, and issues a session JWT.
   */
  public TokenExchangeResult exchangeToken(String token, String orgClerkId) {
    // Resolve org schema
    var mapping =
        orgSchemaMappingRepository
            .findByClerkOrgId(orgClerkId)
            .orElseThrow(() -> new PortalAuthException("Organization not found"));

    String tenantSchema = mapping.getSchemaName();

    // Verify and consume the magic link token within tenant context
    UUID portalContactId;
    try {
      portalContactId =
          ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
              .call(() -> magicLinkService.verifyAndConsumeToken(token));
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
                        .findById(portalContactId)
                        .orElseThrow(
                            () -> new PortalAuthException("Portal contact no longer exists"));

                // Verify contact belongs to the requesting org (cross-tenant isolation)
                if (!contact.getOrgId().equals(orgClerkId)) {
                  throw new PortalAuthException("Invalid magic link token");
                }

                // Verify customer still active
                var customer =
                    customerRepository
                        .findById(contact.getCustomerId())
                        .orElseThrow(
                            () -> new PortalAuthException("Customer account no longer exists"));

                if (!"ACTIVE".equals(customer.getStatus())) {
                  throw new PortalAuthException("Customer account is no longer active");
                }

                // Issue portal JWT
                String portalToken =
                    portalJwtService.issueToken(contact.getCustomerId(), orgClerkId);

                log.info(
                    "Exchanged magic link for portal JWT -- contact {} customer {} in org {}",
                    portalContactId,
                    contact.getCustomerId(),
                    orgClerkId);

                return new TokenExchangeResult(
                    portalToken, contact.getCustomerId(), customer.getName());
              });
    } catch (PortalAuthException e) {
      throw e;
    } catch (Exception e) {
      throw new PortalAuthException("Token exchange failed");
    }
  }

  private boolean isDevProfile() {
    for (String profile : environment.getActiveProfiles()) {
      if ("local".equals(profile)
          || "test".equals(profile)
          || "dev".equals(profile)
          || "e2e".equals(profile)) {
        return true;
      }
    }
    return false;
  }

  // --- Result DTOs ---

  public record MagicLinkResult(String message, String magicLink) {}

  public record TokenExchangeResult(String token, UUID customerId, String customerName) {}
}

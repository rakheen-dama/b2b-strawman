package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.directory.PortalContactDirectoryEntry;
import io.b2mash.b2b.b2bstrawman.portal.directory.PortalContactDirectoryRepository;
import io.b2mash.b2b.b2bstrawman.portal.directory.PortalContactDirectoryService;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Service encapsulating portal authentication orchestration: magic link requests and token
 * exchanges. Extracted from PortalAuthController to keep the controller thin and testable. Since
 * Phase 88 (ADR-351) a magic link request may carry the email alone: the global contact directory
 * selects the firms to try and one branded link is sent per firm, minted inside that firm's tenant.
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
  private final PortalContactDirectoryRepository directoryRepository;
  private final PortalLoginRateLimiter loginRateLimiter;

  public PortalAuthService(
      MagicLinkService magicLinkService,
      PortalJwtService portalJwtService,
      PortalContactRepository portalContactRepository,
      CustomerRepository customerRepository,
      OrgSchemaMappingRepository orgSchemaMappingRepository,
      Environment environment,
      PortalContactDirectoryRepository directoryRepository,
      PortalLoginRateLimiter loginRateLimiter) {
    this.magicLinkService = magicLinkService;
    this.portalJwtService = portalJwtService;
    this.portalContactRepository = portalContactRepository;
    this.customerRepository = customerRepository;
    this.orgSchemaMappingRepository = orgSchemaMappingRepository;
    this.environment = environment;
    this.directoryRepository = directoryRepository;
    this.loginRateLimiter = loginRateLimiter;
  }

  /**
   * Requests portal sign-in links (Phase 88 U11, ADR-351). {@code orgClerkId} is optional: with it,
   * only that firm is tried and the directory is not consulted (fast path — ADR-351 "{@code
   * ?orgId=} short-circuits the directory lookup"); without it (or blank), the global contact
   * directory selects every firm that routes the email. For each firm the org is resolved through
   * {@code org_schema_mapping} (never the directory's {@code tenant_schema}), and the ACTIVE
   * contact is looked up, the token minted and the branded email sent inside that tenant by the
   * unchanged {@link MagicLinkService}. Always returns the same generic message for 0, 1 or N
   * firms; in dev/test profiles the first generated link (directory order) is echoed for testing.
   *
   * @throws io.b2mash.b2b.b2bstrawman.exception.TooManyRequestsException when the per-email cap (3
   *     / 15 min across firms) is exceeded — counted before any lookup, so it leaks nothing
   */
  public MagicLinkResult requestMagicLink(
      String email, @Nullable String orgClerkId, String remoteAddr) {
    String emailLower = PortalContactDirectoryService.normalise(email);
    loginRateLimiter.checkEmail(emailLower, remoteAddr);

    String magicLink = null;
    for (String targetOrgId : resolveTargetOrgIds(emailLower, orgClerkId)) {
      String link = sendBrandedLink(emailLower, targetOrgId, remoteAddr);
      if (magicLink == null) {
        magicLink = link;
      }
    }
    return new MagicLinkResult(GENERIC_MESSAGE, magicLink);
  }

  /** An explicit org is the only target; otherwise every firm the directory routes. */
  private List<String> resolveTargetOrgIds(String emailLower, @Nullable String orgClerkId) {
    if (orgClerkId != null && !orgClerkId.isBlank()) {
      return List.of(orgClerkId);
    }
    return directoryRepository.findAll(emailLower).stream()
        .map(PortalContactDirectoryEntry::orgId)
        .toList();
  }

  /**
   * The existing per-tenant path for one firm: org → schema/shard via {@code org_schema_mapping},
   * then contact lookup + token + branded email inside that tenant. Returns the dev-profile echo
   * link, or {@code null} when nothing was sent. Never throws: an unknown org, a missing/inactive
   * contact, the per-contact cap or any send failure for one firm must neither abort the other
   * firms nor change the response (no enumeration). Our own log lines never include the email;
   * unexpected failures attach the exception for diagnosis.
   */
  private @Nullable String sendBrandedLink(
      String emailLower, String orgClerkId, String remoteAddr) {
    try {
      var mapping = orgSchemaMappingRepository.findByClerkOrgId(orgClerkId).orElse(null);
      if (mapping == null) {
        return null; // unknown org, or a stale directory row for a deleted org
      }
      return RequestScopes.callForTenantOnShard(
          mapping.getSchemaName(),
          null,
          mapping.getShardId(),
          () -> {
            PortalContact contact =
                portalContactRepository
                    .findPreferredActiveByEmailLowerAndOrgId(emailLower, orgClerkId)
                    .orElse(null);
            if (contact == null) {
              return null; // not a contact here, or SUSPENDED/ARCHIVED — generic response
            }
            String rawToken = magicLinkService.generateToken(contact.getId(), remoteAddr);
            log.info(
                "Generated magic link for portal contact {} in org {}",
                contact.getId(),
                orgClerkId);
            return isDevProfile()
                ? "/auth/exchange?token=" + rawToken + "&orgId=" + orgClerkId
                : null;
          });
    } catch (PortalAuthException e) {
      // PortalAuthException("Too many login attempts") from the per-contact cap: swallowed so a
      // known contact is indistinguishable from an unknown email. Expected — no message/stack.
      log.warn("Magic link not issued for org {} ({})", orgClerkId, e.getClass().getSimpleName());
      return null;
    } catch (RuntimeException e) {
      // Unexpected DB/shard/SMTP fault: keep the cause chain for diagnosis; response unchanged.
      log.error(
          "Magic link not issued for org {} ({})", orgClerkId, e.getClass().getSimpleName(), e);
      return null;
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
          RequestScopes.callForTenantOnShard(
              tenantSchema,
              null,
              mapping.getShardId(),
              () -> magicLinkService.verifyAndConsumeToken(token));
    } catch (PortalAuthException e) {
      throw e;
    } catch (Exception e) {
      throw new PortalAuthException("Token verification failed");
    }

    // Load portal contact and customer within tenant context
    try {
      return RequestScopes.callForTenantOnShard(
          tenantSchema,
          null,
          mapping.getShardId(),
          () -> {
            PortalContact contact =
                portalContactRepository
                    .findById(portalContactId)
                    .orElseThrow(() -> new PortalAuthException("Portal contact no longer exists"));

            // Verify contact belongs to the requesting org (cross-tenant isolation)
            if (!contact.getOrgId().equals(orgClerkId)) {
              throw new PortalAuthException("Invalid magic link token");
            }

            // Verify customer still active
            var customer =
                customerRepository
                    .findById(contact.getCustomerId())
                    .orElseThrow(() -> new PortalAuthException("Client account no longer exists"));

            if (!"ACTIVE".equals(customer.getStatus())) {
              throw new PortalAuthException("Client account is no longer active");
            }

            // Issue portal JWT
            String portalToken = portalJwtService.issueToken(contact.getCustomerId(), orgClerkId);

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

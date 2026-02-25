package io.b2mash.b2b.b2bstrawman.portal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates and verifies time-limited magic link tokens for customer portal access. Tokens are
 * cryptographically random, stored as SHA-256 hashes in the database. Single-use enforcement via
 * database state (used_at column). Rate limited to 3 tokens per contact per 5 minutes.
 */
@Service
public class MagicLinkService {

  private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);
  private static final int TOKEN_BYTES = 32;
  private static final long TOKEN_TTL_MINUTES = 15;
  private static final int MAX_TOKENS_PER_5_MINUTES = 3;

  private final MagicLinkTokenRepository tokenRepository;
  private final PortalContactRepository portalContactRepository;
  private final PortalEmailService portalEmailService;
  private final SecureRandom secureRandom;
  private final String appBaseUrl;

  public MagicLinkService(
      MagicLinkTokenRepository tokenRepository,
      PortalContactRepository portalContactRepository,
      PortalEmailService portalEmailService,
      @Value("${docteams.app.base-url:http://localhost:3000}") String appBaseUrl) {
    this.tokenRepository = tokenRepository;
    this.portalContactRepository = portalContactRepository;
    this.portalEmailService = portalEmailService;
    this.appBaseUrl = appBaseUrl;
    try {
      this.secureRandom = SecureRandom.getInstanceStrong();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("No strong SecureRandom available", e);
    }
  }

  /**
   * Generates a cryptographically random magic link token for the given portal contact.
   *
   * @param portalContactId the portal contact UUID
   * @param createdIp the IP address of the requester (nullable)
   * @return the raw token string (URL-safe Base64) to embed in the magic link
   * @throws PortalAuthException if rate limit is exceeded (3 tokens per 5 minutes)
   */
  @Transactional
  public String generateToken(UUID portalContactId, String createdIp) {
    // Rate-limit check: max 3 tokens per contact per 5 minutes
    long recentCount =
        tokenRepository.countByPortalContactIdAndCreatedAtAfter(
            portalContactId, Instant.now().minus(5, ChronoUnit.MINUTES));
    if (recentCount >= MAX_TOKENS_PER_5_MINUTES) {
      throw new PortalAuthException("Too many login attempts");
    }

    // Generate 32 bytes of cryptographic randomness
    byte[] tokenBytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(tokenBytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

    // SHA-256 hash the raw token for storage
    String tokenHash = hashToken(rawToken);

    // Persist the token hash
    Instant expiresAt = Instant.now().plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES);
    var magicLinkToken = new MagicLinkToken(portalContactId, tokenHash, expiresAt, createdIp);
    magicLinkToken = tokenRepository.save(magicLinkToken);

    log.debug("Generated magic link token for portal contact {}", portalContactId);

    // Send magic link email (fire-and-forget)
    try {
      var savedToken = magicLinkToken;
      portalContactRepository
          .findById(portalContactId)
          .ifPresent(
              contact -> {
                String magicLinkUrl = appBaseUrl + "/portal/auth?token=" + rawToken;
                portalEmailService.sendMagicLinkEmail(contact, magicLinkUrl, savedToken.getId());
              });
    } catch (Exception e) {
      log.warn("Failed to send magic link email for contact {}", portalContactId, e);
    }

    return rawToken;
  }

  /**
   * Verifies and consumes a magic link token. The token is looked up by its SHA-256 hash, validated
   * for expiry and single-use, then marked as consumed.
   *
   * @param rawToken the raw token string from the magic link
   * @return the portal contact UUID associated with the token
   * @throws PortalAuthException if the token is invalid, expired, or already consumed
   */
  @Transactional
  public UUID verifyAndConsumeToken(String rawToken) {
    String tokenHash = hashToken(rawToken);

    MagicLinkToken token =
        tokenRepository
            .findByTokenHashForUpdate(tokenHash)
            .orElseThrow(() -> new PortalAuthException("Invalid magic link token"));

    if (token.isExpired()) {
      throw new PortalAuthException("Magic link has expired");
    }

    if (token.isUsed()) {
      throw new PortalAuthException("Magic link has already been used");
    }

    token.markUsed();
    tokenRepository.save(token);

    log.debug(
        "Verified and consumed magic link token for portal contact {}", token.getPortalContactId());
    return token.getPortalContactId();
  }

  /** Hashes a raw token string using SHA-256, returning the hex-encoded hash. */
  private String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}

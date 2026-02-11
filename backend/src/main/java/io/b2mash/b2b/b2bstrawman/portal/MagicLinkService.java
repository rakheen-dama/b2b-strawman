package io.b2mash.b2b.b2bstrawman.portal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Generates and verifies time-limited magic link tokens for customer portal access. Tokens are
 * stateless JWTs signed with HMAC-SHA256, valid for 15 minutes. Single-use enforcement via Caffeine
 * cache of consumed token IDs.
 */
@Service
public class MagicLinkService {

  private static final Logger log = LoggerFactory.getLogger(MagicLinkService.class);
  private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

  private final byte[] secret;

  /** Cache of consumed magic link token JTIs to enforce single-use. */
  private final Cache<String, Boolean> consumedTokens =
      Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(Duration.ofMinutes(30)).build();

  public MagicLinkService(@Value("${portal.jwt.secret}") String portalJwtSecret) {
    this.secret = portalJwtSecret.getBytes();
  }

  /** Identity extracted from a verified magic link token. */
  public record CustomerIdentity(UUID customerId, String clerkOrgId) {}

  /**
   * Generates a signed magic link token embedding customer ID and org ID.
   *
   * @param customerId the customer UUID
   * @param clerkOrgId the Clerk org ID for tenant resolution
   * @return signed JWT string (15-minute TTL)
   */
  public String generateToken(UUID customerId, String clerkOrgId) {
    try {
      Instant now = Instant.now();
      var claims =
          new JWTClaimsSet.Builder()
              .jwtID(UUID.randomUUID().toString())
              .subject(customerId.toString())
              .claim("org_id", clerkOrgId)
              .claim("type", "magic_link")
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plus(TOKEN_TTL)))
              .build();

      var signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      JWSSigner signer = new MACSigner(secret);
      signedJwt.sign(signer);

      log.debug("Generated magic link token for customer {} in org {}", customerId, clerkOrgId);
      return signedJwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to sign magic link token", e);
    }
  }

  /**
   * Verifies a magic link token: validates signature, checks expiry, enforces single-use.
   *
   * @param token the JWT string from the magic link
   * @return customer identity if valid
   * @throws PortalAuthException if the token is invalid, expired, or already consumed
   */
  public CustomerIdentity verifyToken(String token) {
    try {
      var signedJwt = SignedJWT.parse(token);
      JWSVerifier verifier = new MACVerifier(secret);

      if (!signedJwt.verify(verifier)) {
        throw new PortalAuthException("Invalid magic link token signature");
      }

      var claims = signedJwt.getJWTClaimsSet();

      // Check expiry
      if (claims.getExpirationTime() == null
          || claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
        throw new PortalAuthException("Magic link has expired");
      }

      // Check type
      String type = claims.getStringClaim("type");
      if (!"magic_link".equals(type)) {
        throw new PortalAuthException("Invalid token type");
      }

      // Enforce single-use via JTI
      String jti = claims.getJWTID();
      if (jti == null) {
        throw new PortalAuthException("Token missing JTI");
      }
      if (consumedTokens.getIfPresent(jti) != null) {
        throw new PortalAuthException("Magic link has already been used");
      }
      consumedTokens.put(jti, Boolean.TRUE);

      UUID customerId = UUID.fromString(claims.getSubject());
      String orgId = claims.getStringClaim("org_id");

      log.debug("Verified magic link token for customer {} in org {}", customerId, orgId);
      return new CustomerIdentity(customerId, orgId);
    } catch (ParseException | JOSEException e) {
      throw new PortalAuthException("Invalid magic link token: " + e.getMessage());
    }
  }
}

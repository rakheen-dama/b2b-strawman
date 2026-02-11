package io.b2mash.b2b.b2bstrawman.portal;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
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
 * Issues and verifies short-lived portal JWTs (1 hour) for authenticated customer sessions. These
 * are separate from Clerk JWTs and used exclusively for {@code /portal/**} endpoints.
 */
@Service
public class PortalJwtService {

  private static final Logger log = LoggerFactory.getLogger(PortalJwtService.class);
  private static final Duration SESSION_TTL = Duration.ofHours(1);

  private final byte[] secret;

  public PortalJwtService(@Value("${portal.jwt.secret}") String portalJwtSecret) {
    this.secret = portalJwtSecret.getBytes(StandardCharsets.UTF_8);
  }

  /** Claims extracted from a verified portal JWT. */
  public record PortalClaims(UUID customerId, String clerkOrgId) {}

  /**
   * Issues a portal session JWT with customer and org claims.
   *
   * @param customerId the authenticated customer UUID
   * @param clerkOrgId the Clerk org ID for tenant resolution
   * @return signed JWT string (1-hour TTL)
   */
  public String issueToken(UUID customerId, String clerkOrgId) {
    try {
      Instant now = Instant.now();
      var claims =
          new JWTClaimsSet.Builder()
              .jwtID(UUID.randomUUID().toString())
              .subject(customerId.toString())
              .claim("org_id", clerkOrgId)
              .claim("type", "customer")
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plus(SESSION_TTL)))
              .build();

      var signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      JWSSigner signer = new MACSigner(secret);
      signedJwt.sign(signer);

      log.debug("Issued portal JWT for customer {} in org {}", customerId, clerkOrgId);
      return signedJwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to sign portal JWT", e);
    }
  }

  /**
   * Verifies a portal JWT: validates signature, checks expiry, extracts claims.
   *
   * @param token the Bearer token from the portal request
   * @return portal claims if valid
   * @throws PortalAuthException if the token is invalid or expired
   */
  public PortalClaims verifyToken(String token) {
    try {
      var signedJwt = SignedJWT.parse(token);
      JWSVerifier verifier = new MACVerifier(secret);

      if (!signedJwt.verify(verifier)) {
        throw new PortalAuthException("Invalid portal token signature");
      }

      var claims = signedJwt.getJWTClaimsSet();

      // Check expiry
      if (claims.getExpirationTime() == null
          || claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
        throw new PortalAuthException("Portal session has expired");
      }

      // Check type
      String type = claims.getStringClaim("type");
      if (!"customer".equals(type)) {
        throw new PortalAuthException("Invalid token type for portal access");
      }

      UUID customerId = UUID.fromString(claims.getSubject());
      String orgId = claims.getStringClaim("org_id");

      return new PortalClaims(customerId, orgId);
    } catch (ParseException | JOSEException e) {
      throw new PortalAuthException("Invalid portal token: " + e.getMessage());
    }
  }
}

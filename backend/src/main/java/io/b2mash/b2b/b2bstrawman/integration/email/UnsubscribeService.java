package io.b2mash.b2b.b2bstrawman.integration.email;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreference;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreferenceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UnsubscribeService {

  private static final Logger log = LoggerFactory.getLogger(UnsubscribeService.class);
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final Pattern TENANT_SCHEMA_PATTERN = Pattern.compile("^tenant_[0-9a-f]{12}$");

  private final String unsubscribeSecret;
  private final NotificationPreferenceRepository notificationPreferenceRepository;

  public UnsubscribeService(
      @Value("${docteams.email.unsubscribe-secret:}") String unsubscribeSecret,
      NotificationPreferenceRepository notificationPreferenceRepository) {
    this.unsubscribeSecret = unsubscribeSecret;
    this.notificationPreferenceRepository = notificationPreferenceRepository;
  }

  public String generateToken(UUID memberId, String notificationType, String tenantSchema) {
    validateSecretConfigured();
    var encoder = Base64.getUrlEncoder().withoutPadding();
    String payloadStr = memberId.toString() + ":" + notificationType + ":" + tenantSchema;
    byte[] payloadBytes = payloadStr.getBytes(StandardCharsets.UTF_8);
    byte[] hmac = computeHmac(payloadBytes);
    return encoder.encodeToString(payloadBytes) + ":" + encoder.encodeToString(hmac);
  }

  public UnsubscribePayload verifyToken(String token) {
    validateSecretConfigured();
    var decoder = Base64.getUrlDecoder();

    int separatorIndex = token.lastIndexOf(':');
    if (separatorIndex < 0) {
      throw new InvalidStateException("Invalid Token", "Malformed unsubscribe token");
    }

    String encodedPayload = token.substring(0, separatorIndex);
    String encodedHmac = token.substring(separatorIndex + 1);

    byte[] payloadBytes;
    byte[] providedHmac;
    try {
      payloadBytes = decoder.decode(encodedPayload);
      providedHmac = decoder.decode(encodedHmac);
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException("Invalid Token", "Malformed unsubscribe token");
    }

    byte[] expectedHmac = computeHmac(payloadBytes);
    if (!MessageDigest.isEqual(expectedHmac, providedHmac)) {
      throw new InvalidStateException("Invalid Token", "Invalid unsubscribe token");
    }

    String payloadStr = new String(payloadBytes, StandardCharsets.UTF_8);
    String[] parts = payloadStr.split(":", 3);
    if (parts.length != 3) {
      throw new InvalidStateException("Invalid Token", "Malformed unsubscribe token payload");
    }

    try {
      UUID memberId = UUID.fromString(parts[0]);
      return new UnsubscribePayload(memberId, parts[1], parts[2]);
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException("Invalid Token", "Malformed unsubscribe token payload");
    }
  }

  public String processUnsubscribe(String token) {
    UnsubscribePayload payload = verifyToken(token);

    if (!TENANT_SCHEMA_PATTERN.matcher(payload.tenantSchema()).matches()) {
      throw new InvalidStateException(
          "Invalid Token", "Invalid tenant schema in unsubscribe token");
    }

    ScopedValue.where(RequestScopes.TENANT_ID, payload.tenantSchema())
        .run(
            () -> {
              var pref =
                  notificationPreferenceRepository
                      .findByMemberIdAndNotificationType(
                          payload.memberId(), payload.notificationType())
                      .orElseGet(
                          () ->
                              new NotificationPreference(
                                  payload.memberId(), payload.notificationType(), true, false));
              pref.setEmailEnabled(false);
              notificationPreferenceRepository.save(pref);
            });

    log.info(
        "Unsubscribed member {} from {} emails in {}",
        payload.memberId(),
        payload.notificationType(),
        payload.tenantSchema());

    return buildConfirmationHtml(payload.notificationType());
  }

  private byte[] computeHmac(byte[] data) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(
          new SecretKeySpec(unsubscribeSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      return mac.doFinal(data);
    } catch (Exception e) {
      throw new InvalidStateException("HMAC Error", "Failed to compute HMAC: " + e.getMessage());
    }
  }

  private void validateSecretConfigured() {
    if (unsubscribeSecret == null || unsubscribeSecret.isBlank()) {
      log.warn("Unsubscribe secret is not configured (docteams.email.unsubscribe-secret)");
      throw new InvalidStateException(
          "Not Configured", "Unsubscribe functionality is not configured");
    }
  }

  private String buildConfirmationHtml(String notificationType) {
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <title>Unsubscribed</title>
          <style>
            body { font-family: sans-serif; max-width: 600px; margin: 80px auto; text-align: center; color: #333; }
            h1 { font-size: 1.5rem; margin-bottom: 1rem; }
            p  { color: #666; }
          </style>
        </head>
        <body>
          <h1>You have been unsubscribed</h1>
          <p>You have been unsubscribed from <strong>%s</strong> emails.</p>
          <p>You can re-enable email notifications in your DocTeams settings.</p>
        </body>
        </html>
        """
        .formatted(escapeHtml(notificationType));
  }

  private static String escapeHtml(String input) {
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}

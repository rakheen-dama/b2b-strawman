package io.b2mash.b2b.b2bstrawman.integration.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UnsubscribeServiceTest {

  private final UnsubscribeService service =
      new UnsubscribeService("test-secret-for-unit-tests", null);

  @Test
  void generates_valid_token() {
    UUID memberId = UUID.randomUUID();
    String token = service.generateToken(memberId, "COMMENT_ADDED", "tenant_abcdef012345");

    assertThat(token).isNotNull();
    assertThat(token).contains(":");

    // Token should have base64url-encoded payload and HMAC separated by ':'
    // The payload itself contains colons, so use lastIndexOf
    int lastColon = token.lastIndexOf(':');
    assertThat(lastColon).isGreaterThan(0);
    String encodedPayload = token.substring(0, lastColon);
    String encodedHmac = token.substring(lastColon + 1);
    assertThat(encodedPayload).isNotBlank();
    assertThat(encodedHmac).isNotBlank();
  }

  @Test
  void verifies_correct_payload() {
    UUID memberId = UUID.randomUUID();
    String notificationType = "TASK_ASSIGNED";
    String tenantSchema = "tenant_abcdef012345";

    String token = service.generateToken(memberId, notificationType, tenantSchema);
    UnsubscribePayload payload = service.verifyToken(token);

    assertThat(payload.memberId()).isEqualTo(memberId);
    assertThat(payload.notificationType()).isEqualTo(notificationType);
    assertThat(payload.tenantSchema()).isEqualTo(tenantSchema);
  }

  @Test
  void rejects_tampered_token() {
    UUID memberId = UUID.randomUUID();
    String token = service.generateToken(memberId, "COMMENT_ADDED", "tenant_abcdef012345");

    // Tamper with the HMAC segment
    int lastColon = token.lastIndexOf(':');
    String tamperedToken =
        token.substring(0, lastColon) + ":AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    assertThatThrownBy(() -> service.verifyToken(tamperedToken))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void rejects_truncated_token() {
    // Token with no ':' separator at all
    assertThatThrownBy(() -> service.verifyToken("noSeparatorHere"))
        .isInstanceOf(InvalidStateException.class);
  }

  @Test
  void different_inputs_different_tokens() {
    UUID memberId = UUID.randomUUID();
    String tenantSchema = "tenant_abcdef012345";

    String token1 = service.generateToken(memberId, "COMMENT_ADDED", tenantSchema);
    String token2 = service.generateToken(memberId, "TASK_ASSIGNED", tenantSchema);

    assertThat(token1).isNotEqualTo(token2);
  }
}

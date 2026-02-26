package io.b2mash.b2b.b2bstrawman.integration.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreference;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreferenceRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class UnsubscribeServiceTest {

  private final UnsubscribeService service =
      new UnsubscribeService("test-secret-for-unit-tests", null, null);

  @Test
  void generates_valid_token() {
    UUID memberId = UUID.randomUUID();
    String token = service.generateToken(memberId, "COMMENT_ADDED", "tenant_abcdef012345");

    assertThat(token).isNotNull();
    assertThat(token).contains(":");

    // Token should have base64url-encoded payload and HMAC separated by ':'
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

  @SuppressWarnings("unchecked")
  private static TransactionTemplate mockTransactionTemplate() {
    TransactionTemplate mockTx = mock(TransactionTemplate.class);
    doAnswer(
            invocation -> {
              Consumer<TransactionStatus> action = invocation.getArgument(0);
              action.accept(null);
              return null;
            })
        .when(mockTx)
        .executeWithoutResult(any());
    return mockTx;
  }

  @Test
  void html_escapes_notification_type_in_confirmation() {
    NotificationPreferenceRepository mockRepo = mock(NotificationPreferenceRepository.class);
    TransactionTemplate mockTx = mockTransactionTemplate();

    when(mockRepo.findByMemberIdAndNotificationType(any(), any()))
        .thenReturn(Optional.of(new NotificationPreference(UUID.randomUUID(), "x", true, true)));

    var serviceWithRepo = new UnsubscribeService("test-secret-for-unit-tests", mockRepo, mockTx);

    UUID memberId = UUID.randomUUID();
    String xssType = "<script>alert(1)</script>";
    String tenantSchema = "tenant_abcdef012345";

    String token = serviceWithRepo.generateToken(memberId, xssType, tenantSchema);
    String html = serviceWithRepo.processUnsubscribe(token);

    assertThat(html).doesNotContain("<script>");
    assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
  }

  @Test
  void processUnsubscribe_disables_email_preference() {
    NotificationPreferenceRepository mockRepo = mock(NotificationPreferenceRepository.class);
    TransactionTemplate mockTx = mockTransactionTemplate();

    UUID memberId = UUID.randomUUID();
    var existingPref = new NotificationPreference(memberId, "COMMENT_ADDED", true, true);
    when(mockRepo.findByMemberIdAndNotificationType(eq(memberId), eq("COMMENT_ADDED")))
        .thenReturn(Optional.of(existingPref));

    var serviceWithRepo = new UnsubscribeService("test-secret-for-unit-tests", mockRepo, mockTx);

    String token = serviceWithRepo.generateToken(memberId, "COMMENT_ADDED", "tenant_abcdef012345");
    String html = serviceWithRepo.processUnsubscribe(token);

    assertThat(existingPref.isEmailEnabled()).isFalse();
    verify(mockRepo).save(existingPref);
    assertThat(html).contains("unsubscribed");
    assertThat(html).contains("COMMENT_ADDED");
  }
}

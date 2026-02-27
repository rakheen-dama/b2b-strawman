package io.b2mash.b2b.b2bstrawman.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcceptanceRequestTest {

  private static final UUID DOC_ID = UUID.randomUUID();
  private static final UUID CONTACT_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();
  private static final Instant EXPIRES_AT = Instant.now().plus(7, ChronoUnit.DAYS);

  private AcceptanceRequest buildRequest() {
    return new AcceptanceRequest(
        DOC_ID, CONTACT_ID, CUSTOMER_ID, "token-123", EXPIRES_AT, MEMBER_ID);
  }

  @Test
  void constructor_sets_required_fields_and_defaults() {
    var request = buildRequest();

    assertThat(request.getGeneratedDocumentId()).isEqualTo(DOC_ID);
    assertThat(request.getPortalContactId()).isEqualTo(CONTACT_ID);
    assertThat(request.getCustomerId()).isEqualTo(CUSTOMER_ID);
    assertThat(request.getRequestToken()).isEqualTo("token-123");
    assertThat(request.getExpiresAt()).isEqualTo(EXPIRES_AT);
    assertThat(request.getSentByMemberId()).isEqualTo(MEMBER_ID);
    assertThat(request.getStatus()).isEqualTo(AcceptanceStatus.PENDING);
    assertThat(request.getReminderCount()).isZero();
    assertThat(request.getId()).isNull();
    assertThat(request.getSentAt()).isNull();
    assertThat(request.getViewedAt()).isNull();
    assertThat(request.getAcceptedAt()).isNull();
    assertThat(request.getRevokedAt()).isNull();
    assertThat(request.getCreatedAt()).isNull();
    assertThat(request.getUpdatedAt()).isNull();
  }

  @Test
  void markSent_sets_sentAt_and_status() {
    var request = buildRequest();

    request.markSent();

    assertThat(request.getStatus()).isEqualTo(AcceptanceStatus.SENT);
    assertThat(request.getSentAt()).isNotNull();
  }

  @Test
  void markViewed_sets_viewedAt_and_status() {
    var request = buildRequest();
    request.markSent();
    var viewedAt = Instant.now();

    request.markViewed(viewedAt);

    assertThat(request.getStatus()).isEqualTo(AcceptanceStatus.VIEWED);
    assertThat(request.getViewedAt()).isEqualTo(viewedAt);
  }

  @Test
  void markViewed_idempotent_for_viewed() {
    var request = buildRequest();
    request.markSent();
    var viewedAt = Instant.now();
    request.markViewed(viewedAt);

    // calling again should be a no-op
    request.markViewed(Instant.now().plus(1, ChronoUnit.HOURS));

    assertThat(request.getStatus()).isEqualTo(AcceptanceStatus.VIEWED);
    assertThat(request.getViewedAt()).isEqualTo(viewedAt);
  }

  @Test
  void markAccepted_records_metadata_and_status() {
    var request = buildRequest();
    request.markSent();

    request.markAccepted("John Doe", "192.168.1.1", "Mozilla/5.0");

    assertThat(request.getStatus()).isEqualTo(AcceptanceStatus.ACCEPTED);
    assertThat(request.getAcceptorName()).isEqualTo("John Doe");
    assertThat(request.getAcceptorIpAddress()).isEqualTo("192.168.1.1");
    assertThat(request.getAcceptorUserAgent()).isEqualTo("Mozilla/5.0");
    assertThat(request.getAcceptedAt()).isNotNull();
  }

  @Test
  void markRevoked_sets_revokedAt_and_revokedBy() {
    var request = buildRequest();
    var revokedBy = UUID.randomUUID();

    request.markRevoked(revokedBy);

    assertThat(request.getStatus()).isEqualTo(AcceptanceStatus.REVOKED);
    assertThat(request.getRevokedAt()).isNotNull();
    assertThat(request.getRevokedByMemberId()).isEqualTo(revokedBy);
  }

  @Test
  void markExpired_sets_status() {
    var request = buildRequest();
    request.markSent();

    request.markExpired();

    assertThat(request.getStatus()).isEqualTo(AcceptanceStatus.EXPIRED);
  }

  @Test
  void recordReminder_increments_count_and_sets_lastRemindedAt() {
    var request = buildRequest();
    request.markSent();

    request.recordReminder();
    assertThat(request.getReminderCount()).isEqualTo(1);
    assertThat(request.getLastRemindedAt()).isNotNull();

    request.recordReminder();
    assertThat(request.getReminderCount()).isEqualTo(2);
  }

  @Test
  void isActive_returns_true_for_active_statuses() {
    var pending = buildRequest();
    assertThat(pending.isActive()).isTrue();

    var sent = buildRequest();
    sent.markSent();
    assertThat(sent.isActive()).isTrue();

    var viewed = buildRequest();
    viewed.markSent();
    viewed.markViewed(Instant.now());
    assertThat(viewed.isActive()).isTrue();

    var accepted = buildRequest();
    accepted.markSent();
    accepted.markAccepted("Name", "127.0.0.1", "UA");
    assertThat(accepted.isActive()).isFalse();

    var expired = buildRequest();
    expired.markSent();
    expired.markExpired();
    assertThat(expired.isActive()).isFalse();

    var revoked = buildRequest();
    revoked.markRevoked(UUID.randomUUID());
    assertThat(revoked.isActive()).isFalse();
  }

  @Test
  void markViewed_throws_for_terminal_status() {
    var accepted = buildRequest();
    accepted.markSent();
    accepted.markAccepted("Name", "127.0.0.1", "UA");
    assertThatThrownBy(() -> accepted.markViewed(Instant.now()))
        .isInstanceOf(InvalidStateException.class);

    var expired = buildRequest();
    expired.markSent();
    expired.markExpired();
    assertThatThrownBy(() -> expired.markViewed(Instant.now()))
        .isInstanceOf(InvalidStateException.class);

    var revoked = buildRequest();
    revoked.markRevoked(UUID.randomUUID());
    assertThatThrownBy(() -> revoked.markViewed(Instant.now()))
        .isInstanceOf(InvalidStateException.class);
  }
}

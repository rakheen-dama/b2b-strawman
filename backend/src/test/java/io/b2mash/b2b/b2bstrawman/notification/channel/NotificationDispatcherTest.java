package io.b2mash.b2b.b2bstrawman.notification.channel;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreference;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreferenceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationDispatcherTest {

  private NotificationChannel inAppChannel;
  private NotificationChannel emailChannel;
  private NotificationPreferenceRepository preferenceRepository;
  private NotificationDispatcher dispatcher;

  private Notification notification;
  private static final String RECIPIENT_EMAIL = "user@example.com";
  private static final UUID RECIPIENT_ID = UUID.randomUUID();
  private static final String NOTIFICATION_TYPE = "TASK_ASSIGNED";

  @BeforeEach
  void setUp() {
    inAppChannel = mock(NotificationChannel.class);
    when(inAppChannel.channelId()).thenReturn("in-app");
    when(inAppChannel.isEnabled()).thenReturn(true);

    emailChannel = mock(NotificationChannel.class);
    when(emailChannel.channelId()).thenReturn("email");
    when(emailChannel.isEnabled()).thenReturn(true);

    preferenceRepository = mock(NotificationPreferenceRepository.class);

    dispatcher =
        new NotificationDispatcher(List.of(inAppChannel, emailChannel), preferenceRepository);

    notification =
        new Notification(
            RECIPIENT_ID,
            NOTIFICATION_TYPE,
            "You were assigned a task",
            null,
            "TASK",
            UUID.randomUUID(),
            UUID.randomUUID());
  }

  @Test
  void dispatchesToInAppWhenNoPreferenceExists() {
    when(preferenceRepository.findByMemberIdAndNotificationType(RECIPIENT_ID, NOTIFICATION_TYPE))
        .thenReturn(Optional.empty());

    dispatcher.dispatch(notification, RECIPIENT_EMAIL);

    verify(inAppChannel).deliver(notification, RECIPIENT_EMAIL);
    verify(emailChannel, never()).deliver(any(), any());
  }

  @Test
  void skipsInAppWhenExplicitlyDisabled() {
    var preference = new NotificationPreference(RECIPIENT_ID, NOTIFICATION_TYPE, false, false);
    when(preferenceRepository.findByMemberIdAndNotificationType(RECIPIENT_ID, NOTIFICATION_TYPE))
        .thenReturn(Optional.of(preference));

    dispatcher.dispatch(notification, RECIPIENT_EMAIL);

    verify(inAppChannel, never()).deliver(any(), any());
    verify(emailChannel, never()).deliver(any(), any());
  }

  @Test
  void dispatchesToEmailWhenEmailEnabled() {
    var preference = new NotificationPreference(RECIPIENT_ID, NOTIFICATION_TYPE, true, true);
    when(preferenceRepository.findByMemberIdAndNotificationType(RECIPIENT_ID, NOTIFICATION_TYPE))
        .thenReturn(Optional.of(preference));

    dispatcher.dispatch(notification, RECIPIENT_EMAIL);

    verify(inAppChannel).deliver(notification, RECIPIENT_EMAIL);
    verify(emailChannel).deliver(notification, RECIPIENT_EMAIL);
  }

  @Test
  void skipsEmailByDefault() {
    var preference = new NotificationPreference(RECIPIENT_ID, NOTIFICATION_TYPE, true, false);
    when(preferenceRepository.findByMemberIdAndNotificationType(RECIPIENT_ID, NOTIFICATION_TYPE))
        .thenReturn(Optional.of(preference));

    dispatcher.dispatch(notification, RECIPIENT_EMAIL);

    verify(inAppChannel).deliver(notification, RECIPIENT_EMAIL);
    verify(emailChannel, never()).deliver(any(), any());
  }

  @Test
  void handlesChannelExceptionWithoutPropagating() {
    when(preferenceRepository.findByMemberIdAndNotificationType(RECIPIENT_ID, NOTIFICATION_TYPE))
        .thenReturn(Optional.empty());
    doThrow(new RuntimeException("Channel failure")).when(inAppChannel).deliver(any(), any());

    // Should not throw
    dispatcher.dispatch(notification, RECIPIENT_EMAIL);

    verify(inAppChannel).deliver(notification, RECIPIENT_EMAIL);
    // Email should still not be called (no preference = email disabled by default)
    verify(emailChannel, never()).deliver(any(), any());
  }
}

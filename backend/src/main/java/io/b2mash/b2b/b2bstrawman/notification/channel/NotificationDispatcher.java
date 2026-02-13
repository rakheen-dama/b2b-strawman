package io.b2mash.b2b.b2bstrawman.notification.channel;

import io.b2mash.b2b.b2bstrawman.notification.Notification;
import io.b2mash.b2b.b2bstrawman.notification.NotificationPreferenceRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes notifications to appropriate channels based on user preferences. Channels self-register
 * via constructor injection (Spring collects all NotificationChannel beans).
 */
@Component
public class NotificationDispatcher {

  private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

  private final Map<String, NotificationChannel> channels;
  private final NotificationPreferenceRepository preferenceRepository;

  public NotificationDispatcher(
      List<NotificationChannel> channelBeans,
      NotificationPreferenceRepository preferenceRepository) {
    this.channels =
        channelBeans.stream()
            .filter(NotificationChannel::isEnabled)
            .collect(Collectors.toMap(NotificationChannel::channelId, Function.identity()));
    this.preferenceRepository = preferenceRepository;
  }

  /**
   * Dispatches a notification to all enabled channels based on user preferences.
   *
   * @param notification the persisted notification
   * @param recipientEmail the recipient's email (for email channel)
   */
  public void dispatch(Notification notification, String recipientEmail) {
    var preference =
        preferenceRepository
            .findByMemberIdAndNotificationType(
                notification.getRecipientMemberId(), notification.getType())
            .orElse(null);

    // In-app channel: always dispatch unless explicitly disabled
    boolean inAppEnabled = preference == null || preference.isInAppEnabled();
    if (inAppEnabled) {
      dispatchToChannel("in-app", notification, recipientEmail);
    }

    // Email channel: only dispatch if explicitly enabled (default is false)
    boolean emailEnabled = preference != null && preference.isEmailEnabled();
    if (emailEnabled) {
      dispatchToChannel("email", notification, recipientEmail);
    }
  }

  private void dispatchToChannel(String channelId, Notification notification, String email) {
    var channel = channels.get(channelId);
    if (channel != null) {
      try {
        channel.deliver(notification, email);
      } catch (Exception e) {
        log.warn(
            "Failed to deliver notification via channel={} notificationId={}",
            channelId,
            notification.getId(),
            e);
      }
    }
  }
}

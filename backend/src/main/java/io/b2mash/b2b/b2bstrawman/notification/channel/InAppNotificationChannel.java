package io.b2mash.b2b.b2bstrawman.notification.channel;

import io.b2mash.b2b.b2bstrawman.notification.Notification;
import org.springframework.stereotype.Component;

/**
 * In-app notification channel. The notification is already persisted in the database by the time
 * this channel is invoked -- this is effectively a no-op that exists for symmetry with other
 * channels in the NotificationDispatcher.
 */
@Component
public class InAppNotificationChannel implements NotificationChannel {

  @Override
  public String channelId() {
    return "in-app";
  }

  @Override
  public void deliver(Notification notification, String recipientEmail) {
    // No-op: the notification row was already created by NotificationService.
    // This channel exists for architectural completeness in NotificationDispatcher.
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}

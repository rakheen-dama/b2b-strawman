package io.b2mash.b2b.b2bstrawman.notification.channel;

import io.b2mash.b2b.b2bstrawman.notification.Notification;

/**
 * Abstraction for notification delivery channels. Each channel handles one delivery mechanism
 * (in-app, email, push, etc.).
 */
public interface NotificationChannel {

  /** Unique identifier for this channel (e.g., "in-app", "email"). */
  String channelId();

  /**
   * Delivers a notification via this channel.
   *
   * @param notification the notification to deliver
   * @param recipientEmail the recipient's email address (for email channels)
   */
  void deliver(Notification notification, String recipientEmail);

  /** Whether this channel is currently enabled/available. */
  boolean isEnabled();
}

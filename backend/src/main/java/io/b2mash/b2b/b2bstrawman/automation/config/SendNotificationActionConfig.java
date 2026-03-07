package io.b2mash.b2b.b2bstrawman.automation.config;

public record SendNotificationActionConfig(String recipientType, String title, String message)
    implements ActionConfig {}

package io.b2mash.b2b.b2bstrawman.automation.config;

public record SendEmailActionConfig(String recipientType, String subject, String body)
    implements ActionConfig {}

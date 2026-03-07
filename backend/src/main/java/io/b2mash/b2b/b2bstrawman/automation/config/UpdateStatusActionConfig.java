package io.b2mash.b2b.b2bstrawman.automation.config;

public record UpdateStatusActionConfig(String targetEntityType, String newStatus)
    implements ActionConfig {}

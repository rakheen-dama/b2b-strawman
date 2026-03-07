package io.b2mash.b2b.b2bstrawman.automation.config;

public record StatusChangeTriggerConfig(String fromStatus, String toStatus)
    implements TriggerConfig {}

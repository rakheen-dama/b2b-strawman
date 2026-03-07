package io.b2mash.b2b.b2bstrawman.automation.config;

import java.util.UUID;

public record CreateTaskActionConfig(
    String taskName,
    String taskDescription,
    AssignTo assignTo,
    UUID specificMemberId,
    String taskStatus)
    implements ActionConfig {}

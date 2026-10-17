package io.b2mash.b2b.b2bstrawman.automation.config;

public sealed interface ActionConfig
    permits CreateTaskActionConfig,
        SendNotificationActionConfig,
        SendEmailActionConfig,
        UpdateStatusActionConfig,
        CreateProjectActionConfig,
        AssignMemberActionConfig {}

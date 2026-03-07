package io.b2mash.b2b.b2bstrawman.automation.config;

import java.util.UUID;

public record AssignMemberActionConfig(AssignTo assignTo, UUID specificMemberId, String role)
    implements ActionConfig {}

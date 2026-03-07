package io.b2mash.b2b.b2bstrawman.automation.config;

import java.util.UUID;

public record CreateProjectActionConfig(
    UUID projectTemplateId, String projectName, boolean linkToCustomer) implements ActionConfig {}

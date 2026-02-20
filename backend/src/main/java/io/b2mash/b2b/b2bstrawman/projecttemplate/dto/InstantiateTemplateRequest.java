package io.b2mash.b2b.b2bstrawman.projecttemplate.dto;

import java.util.UUID;

public record InstantiateTemplateRequest(
    String name, UUID customerId, UUID projectLeadMemberId, String description) {}

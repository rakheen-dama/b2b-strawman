package io.b2mash.b2b.b2bstrawman.customerbackend.model;

import java.util.UUID;

public record PortalTaskView(
    UUID id,
    String orgId,
    UUID portalProjectId,
    String name,
    String status,
    String assigneeName,
    int sortOrder) {}

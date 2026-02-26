package io.b2mash.b2b.b2bstrawman.integration.email;

import java.util.UUID;

public record UnsubscribePayload(UUID memberId, String notificationType, String tenantSchema) {}

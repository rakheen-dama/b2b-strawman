package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import java.util.UUID;

public record AiGateApprovedEvent(UUID gateId, String gateType, UUID reviewerId) {}

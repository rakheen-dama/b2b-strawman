package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import java.util.UUID;

public record AiGateExpiredEvent(UUID gateId, String gateType) {}

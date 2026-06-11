package io.b2mash.b2b.b2bstrawman.settings.dto;

import io.b2mash.b2b.b2bstrawman.settings.PortalDigestCadence;
import jakarta.validation.constraints.NotNull;

/** Request to update the firm-wide portal digest email cadence. */
public record UpdatePortalDigestCadenceRequest(
    @NotNull(message = "portalDigestCadence is required")
        PortalDigestCadence portalDigestCadence) {}

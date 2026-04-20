package io.b2mash.b2b.b2bstrawman.customerbackend.dto;

import java.util.List;

/** Aggregate response for {@code GET /portal/trust/summary}. */
public record PortalTrustSummaryResponse(List<PortalTrustMatterSummary> matters) {}

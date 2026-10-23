package io.b2mash.b2b.b2bstrawman.dashboard.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Clients (ONBOARDING/ACTIVE) whose FICA status is not DONE — {@code GET
 * /api/dashboard/fica-outstanding} (Phase 87, Epic 614A, arch §12.2.6). {@code outstandingCount}
 * counts every outstanding client; {@code clients} holds the top {@code limit} by days outstanding.
 */
public record FicaOutstandingResponse(
    int outstandingCount, int totalActiveClients, List<FicaOutstandingClient> clients, int limit) {

  /**
   * One outstanding client. {@code openedAt} is the client's clock-start instant ({@code
   * lifecycleStatusChangedAt}, falling back to {@code createdAt}) — not a request timestamp; {@code
   * requestId} is null unless the information-request branch of the FICA rule decided (ADR-348).
   */
  public record FicaOutstandingClient(
      UUID clientId,
      String clientName,
      String lifecycleStatus,
      String ficaStatus,
      UUID requestId,
      Instant openedAt,
      int daysOutstanding) {}
}

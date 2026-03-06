package io.b2mash.b2b.b2bstrawman.informationrequest;

import java.util.UUID;

/** Placeholder event records for information request lifecycle. Will be replaced in Epic 254A. */
public final class InformationRequestEvents {

  private InformationRequestEvents() {}

  public record InformationRequestSentEvent(UUID requestId) {}

  public record InformationRequestCancelledEvent(UUID requestId) {}

  public record InformationRequestCompletedEvent(UUID requestId) {}

  public record RequestItemAcceptedEvent(UUID requestId, UUID itemId) {}

  public record RequestItemRejectedEvent(UUID requestId, UUID itemId) {}
}

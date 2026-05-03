package io.b2mash.b2b.b2bstrawman.assistant.invocation.applier;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.OutputPayload;
import java.util.UUID;

/**
 * Strategy that applies an approved {@link OutputPayload} to the underlying domain.
 *
 * <p>One bean per concrete payload subtype. The applier is responsible for any payload-specific
 * capability check (delegated to the downstream service), the actual write, and any per-domain
 * audit emission. Registered into {@link OutputApplierRegistry} via Spring component scan.
 */
public interface OutputApplier<T extends OutputPayload> {

  /** The concrete payload subtype this applier handles. */
  Class<T> payloadType();

  /** Apply the payload as the given actor. */
  void apply(T payload, UUID actorId);
}

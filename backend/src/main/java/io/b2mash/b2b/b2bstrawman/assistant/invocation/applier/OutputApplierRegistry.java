package io.b2mash.b2b.b2bstrawman.assistant.invocation.applier;

import io.b2mash.b2b.b2bstrawman.assistant.invocation.payload.OutputPayload;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link OutputApplier} for a given {@link OutputPayload} subtype.
 *
 * <p>Spring autowires the full list of registered applier beans. In 515A no concrete appliers exist
 * yet (513/514/512A introduce them), so the map is empty and {@link #forPayload} throws {@link
 * InvalidStateException}. This is intentional — the foundation slice ships without downstream
 * service integrations.
 */
@Component
public class OutputApplierRegistry {

  private final Map<Class<? extends OutputPayload>, OutputApplier<?>> byType;

  public OutputApplierRegistry(List<OutputApplier<?>> appliers) {
    this.byType =
        appliers.stream().collect(Collectors.toUnmodifiableMap(OutputApplier::payloadType, a -> a));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public OutputApplier forPayload(OutputPayload payload) {
    var applier = byType.get(payload.getClass());
    if (applier == null) {
      throw new InvalidStateException(
          "No applier registered",
          "No OutputApplier registered for payload type: " + payload.getClass().getSimpleName());
    }
    return applier;
  }
}

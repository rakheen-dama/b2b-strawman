package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Plain unit test for {@link JobHandlerRegistry} — no Spring context needed. */
class JobHandlerRegistryTest {

  @Test
  void shouldResolveHandlerByJobType() {
    var handlerA = new StubJobHandler("type_a");
    var handlerB = new StubJobHandler("type_b");
    var registry = new JobHandlerRegistry(List.of(handlerA, handlerB));

    assertThat(registry.getHandler("type_a")).isSameAs(handlerA);
    assertThat(registry.getHandler("type_b")).isSameAs(handlerB);
  }

  @Test
  void shouldThrowIllegalStateExceptionForDuplicateJobType() {
    var handler1 = new StubJobHandler("duplicate_type");
    var handler2 = new StubJobHandler("duplicate_type");

    assertThatThrownBy(() -> new JobHandlerRegistry(List.of(handler1, handler2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate JobHandler registrations")
        .hasMessageContaining("duplicate_type");
  }

  @Test
  void shouldThrowIllegalArgumentExceptionForUnknownJobType() {
    var handler = new StubJobHandler("known_type");
    var registry = new JobHandlerRegistry(List.of(handler));

    assertThatThrownBy(() -> registry.getHandler("unknown_type"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No JobHandler registered for jobType: unknown_type");
  }

  /** Minimal stub handler for unit testing the registry. */
  private static class StubJobHandler implements JobHandler {
    private final String type;

    StubJobHandler(String type) {
      this.type = type;
    }

    @Override
    public String jobType() {
      return type;
    }

    @Override
    public void execute(@Nullable JsonNode payload) {
      // no-op for registry tests
    }
  }
}

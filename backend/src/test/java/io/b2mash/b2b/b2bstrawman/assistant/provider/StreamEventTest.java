package io.b2mash.b2b.b2bstrawman.assistant.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StreamEventTest {

  @Test
  void textDeltaCarriesText() {
    var event = new StreamEvent.TextDelta("Hello");

    assertThat(event.text()).isEqualTo("Hello");
    assertThat(event).isInstanceOf(StreamEvent.class);
  }

  @Test
  void toolUseCarriesFields() {
    var input = Map.<String, Object>of("projectId", "abc-123");
    var event = new StreamEvent.ToolUse("tc_01", "list_projects", input);

    assertThat(event.toolCallId()).isEqualTo("tc_01");
    assertThat(event.toolName()).isEqualTo("list_projects");
    assertThat(event.input()).containsEntry("projectId", "abc-123");
  }

  @Test
  void usageCarriesTokenCounts() {
    var event = new StreamEvent.Usage(100, 50);

    assertThat(event.inputTokens()).isEqualTo(100);
    assertThat(event.outputTokens()).isEqualTo(50);
  }

  @Test
  void doneIsEmptySignal() {
    var event = new StreamEvent.Done();

    assertThat(event).isInstanceOf(StreamEvent.class);
  }

  @Test
  void errorCarriesMessage() {
    var event = new StreamEvent.Error("Rate limit exceeded");

    assertThat(event.message()).isEqualTo("Rate limit exceeded");
  }

  @Test
  void exhaustiveSwitchCoversAllVariants() {
    // This test verifies that the sealed interface permits exactly 5 subtypes.
    // If a new subtype is added without updating this switch, the compiler will
    // produce an error (exhaustive switch on sealed type).
    StreamEvent[] events = {
      new StreamEvent.TextDelta("text"),
      new StreamEvent.ToolUse("id", "name", Map.of()),
      new StreamEvent.Usage(1, 2),
      new StreamEvent.Done(),
      new StreamEvent.Error("err")
    };

    for (var event : events) {
      var label =
          switch (event) {
            case StreamEvent.TextDelta td -> "text:" + td.text();
            case StreamEvent.ToolUse tu -> "tool:" + tu.toolName();
            case StreamEvent.Usage u -> "usage:" + u.inputTokens();
            case StreamEvent.Done d -> "done";
            case StreamEvent.Error e -> "error:" + e.message();
          };
      assertThat(label).isNotBlank();
    }
  }

  @Test
  void allVariantsArePermittedSubtypes() {
    // Verify via reflection that exactly 5 permitted subtypes exist
    var permitted = StreamEvent.class.getPermittedSubclasses();

    assertThat(permitted).hasSize(5);
    assertThat(permitted)
        .extracting(Class::getSimpleName)
        .containsExactlyInAnyOrder("TextDelta", "ToolUse", "Usage", "Done", "Error");
  }
}

package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for {@link LlmJsonParser}. Reproduces AIVERIFY-001: live Claude wraps structured
 * output in a markdown code fence, which a raw {@code ObjectMapper.readValue} rejects on the
 * leading backtick. The parser must tolerate fences and prose preambles while still surfacing
 * genuinely malformed bodies as an {@link InvalidStateException}.
 */
class LlmJsonParserTest {

  private final LlmJsonParser parser = new LlmJsonParser();
  private final ObjectMapper mapper = JsonMapper.builder().build();

  record Sample(String name, int value) {}

  @Test
  void parse_alreadyCleanJson_bindsDirectly() {
    String raw = "{\"name\":\"alice\",\"value\":42}";

    Sample result = parser.parse(mapper, raw, Sample.class);

    assertThat(result.name()).isEqualTo("alice");
    assertThat(result.value()).isEqualTo(42);
  }

  @Test
  void parse_fencedJsonWithLanguageHint_stripsFence() {
    // The exact shape that broke V3: ```json … ``` around the object.
    String raw =
        """
        ```json
        {"name":"bob","value":7}
        ```
        """;

    Sample result = parser.parse(mapper, raw, Sample.class);

    assertThat(result.name()).isEqualTo("bob");
    assertThat(result.value()).isEqualTo(7);
  }

  @Test
  void parse_fencedJsonBareFence_stripsFence() {
    String raw =
        """
        ```
        {"name":"carol","value":3}
        ```
        """;

    Sample result = parser.parse(mapper, raw, Sample.class);

    assertThat(result.name()).isEqualTo("carol");
    assertThat(result.value()).isEqualTo(3);
  }

  @Test
  void parse_uppercaseFenceHint_stripsFence() {
    String raw = "```JSON\n{\"name\":\"dave\",\"value\":9}\n```";

    Sample result = parser.parse(mapper, raw, Sample.class);

    assertThat(result.name()).isEqualTo("dave");
    assertThat(result.value()).isEqualTo(9);
  }

  @Test
  void parse_prosePreambleBeforeJson_slicesObject() {
    String raw = "Here is the analysis you requested:\n\n{\"name\":\"erin\",\"value\":11}";

    Sample result = parser.parse(mapper, raw, Sample.class);

    assertThat(result.name()).isEqualTo("erin");
    assertThat(result.value()).isEqualTo(11);
  }

  @Test
  void parse_proseAroundFencedJson_slicesObject() {
    String raw =
        """
        Sure! Here is the structured result:
        ```json
        {"name":"frank","value":13}
        ```
        Let me know if you need anything else.
        """;

    Sample result = parser.parse(mapper, raw, Sample.class);

    assertThat(result.name()).isEqualTo("frank");
    assertThat(result.value()).isEqualTo(13);
  }

  @Test
  void parse_leadingAndTrailingWhitespace_isTolerated() {
    String raw = "   \n\n  {\"name\":\"grace\",\"value\":5}  \n  ";

    Sample result = parser.parse(mapper, raw, Sample.class);

    assertThat(result.name()).isEqualTo("grace");
    assertThat(result.value()).isEqualTo(5);
  }

  @Test
  void parse_jsonArrayBody_isSliced() {
    String raw = "```json\n[1, 2, 3]\n```";

    int[] result = parser.parse(mapper, raw, int[].class);

    assertThat(result).containsExactly(1, 2, 3);
  }

  @Test
  void parse_trailingProseWithStrayBrace_stopsAtMatchingCloser() {
    // A naive lastIndexOf('}') would extend the slice to the '}' in the prose epilogue and fail.
    String raw = "{\"name\":\"heidi\",\"value\":8}  Note the closing brace } in this sentence.";

    Sample result = parser.parse(mapper, raw, Sample.class);

    assertThat(result.name()).isEqualTo("heidi");
    assertThat(result.value()).isEqualTo(8);
  }

  @Test
  void parse_jsonWithBraceInsideStringValue_isNotTruncated() {
    String raw = "{\"name\":\"value with a } brace inside\",\"value\":4}";

    Sample result = parser.parse(mapper, raw, Sample.class);

    assertThat(result.name()).isEqualTo("value with a } brace inside");
    assertThat(result.value()).isEqualTo(4);
  }

  @Test
  void parse_malformedBody_throwsInvalidStateException() {
    String raw = "this is not json at all, just a sentence";

    assertThatThrownBy(() -> parser.parse(mapper, raw, Sample.class))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("Sample");
  }

  @Test
  void parse_emptyFencedBlock_throwsInvalidStateException() {
    String raw = "```json\n\n```";

    assertThatThrownBy(() -> parser.parse(mapper, raw, Sample.class))
        .isInstanceOf(InvalidStateException.class);
  }
}

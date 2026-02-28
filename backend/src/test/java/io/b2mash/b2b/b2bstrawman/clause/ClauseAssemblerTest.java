package io.b2mash.b2b.b2bstrawman.clause;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClauseAssemblerTest {

  private static final Map<String, Object> SAMPLE_BODY =
      Map.of("type", "doc", "content", List.of());

  private final ClauseAssembler assembler = new ClauseAssembler();

  @Test
  void assembleClauseBlock_emptyList_returnsEmptyString() {
    assertThat(assembler.assembleClauseBlock(List.of())).isEmpty();
  }

  @Test
  void assembleClauseBlock_nullList_returnsEmptyString() {
    assertThat(assembler.assembleClauseBlock(null)).isEmpty();
  }

  @Test
  void assembleClauseBlock_singleClause_wrapsInDiv() {
    // ClauseAssembler now uses legacyBody (for the old Thymeleaf pipeline).
    // New clauses without legacyBody will have empty body output.
    var clause = new Clause("Confidentiality", "confidentiality", SAMPLE_BODY, "General");
    String result = assembler.assembleClauseBlock(List.of(clause));

    assertThat(result).contains("class=\"clause-block\"");
    assertThat(result).contains("data-clause-slug=\"confidentiality\"");
    // legacyBody is null for new clauses — body content must be empty (not leaked/substituted)
    assertThat(result).doesNotContain("null");
    assertThat(result).containsPattern("data-clause-slug=\"confidentiality\">\\s*\n\\s*</div>");
  }

  @Test
  void assembleClauseBlock_multipleClauses_preservesOrder() {
    var clause1 = new Clause("First", "first-clause", SAMPLE_BODY, "General");
    var clause2 = new Clause("Second", "second-clause", SAMPLE_BODY, "Legal");
    String result = assembler.assembleClauseBlock(List.of(clause1, clause2));

    int firstIdx = result.indexOf("data-clause-slug=\"first-clause\"");
    int secondIdx = result.indexOf("data-clause-slug=\"second-clause\"");
    assertThat(firstIdx).isLessThan(secondIdx);
  }

  @Test
  void escapeHtmlAttribute_escapesSpecialCharacters() {
    assertThat(ClauseAssembler.escapeHtmlAttribute("a&b")).isEqualTo("a&amp;b");
    assertThat(ClauseAssembler.escapeHtmlAttribute("a\"b")).isEqualTo("a&quot;b");
    assertThat(ClauseAssembler.escapeHtmlAttribute("a'b")).isEqualTo("a&#39;b");
    assertThat(ClauseAssembler.escapeHtmlAttribute("a<b>c")).isEqualTo("a&lt;b&gt;c");
    assertThat(ClauseAssembler.escapeHtmlAttribute(null)).isEmpty();
  }
}

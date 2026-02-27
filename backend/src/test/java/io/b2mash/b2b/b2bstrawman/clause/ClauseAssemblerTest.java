package io.b2mash.b2b.b2bstrawman.clause;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClauseAssemblerTest {

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
    var clause =
        new Clause("Confidentiality", "confidentiality", "<p>The parties agree...</p>", "General");
    String result = assembler.assembleClauseBlock(List.of(clause));

    assertThat(result).contains("class=\"clause-block\"");
    assertThat(result).contains("data-clause-slug=\"confidentiality\"");
    assertThat(result).contains("<p>The parties agree...</p>");
  }

  @Test
  void assembleClauseBlock_multipleClauses_preservesOrder() {
    var clause1 = new Clause("First", "first-clause", "<p>First body</p>", "General");
    var clause2 = new Clause("Second", "second-clause", "<p>Second body</p>", "Legal");
    String result = assembler.assembleClauseBlock(List.of(clause1, clause2));

    int firstIdx = result.indexOf("data-clause-slug=\"first-clause\"");
    int secondIdx = result.indexOf("data-clause-slug=\"second-clause\"");
    assertThat(firstIdx).isLessThan(secondIdx);
    assertThat(result).contains("<p>First body</p>");
    assertThat(result).contains("<p>Second body</p>");
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

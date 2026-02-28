package io.b2mash.b2b.b2bstrawman.clause;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Assembles clause bodies into a single HTML block for injection into document templates. Each
 * clause body is wrapped in a div with CSS class and data attribute for styling hooks.
 */
@Component
public class ClauseAssembler {

  /**
   * Assembles the given clauses into a single HTML block string. Each clause body is wrapped in a
   * {@code <div class="clause-block" data-clause-slug="...">}.
   *
   * @param clauses the clauses to assemble, in display order
   * @return the assembled HTML string, or empty string if no clauses
   */
  public String assembleClauseBlock(List<Clause> clauses) {
    if (clauses == null || clauses.isEmpty()) {
      return "";
    }
    var sb = new StringBuilder();
    for (var clause : clauses) {
      String body = clause.getLegacyBody() != null ? clause.getLegacyBody() : "";
      sb.append("<div class=\"clause-block\" data-clause-slug=\"")
          .append(escapeHtmlAttribute(clause.getSlug()))
          .append("\">\n")
          .append(body)
          .append("\n</div>\n");
    }
    return sb.toString();
  }

  /**
   * Escapes a string for safe use in an HTML attribute value. Handles &amp;, &lt;, &gt;, &quot;,
   * and &#39; characters.
   */
  static String escapeHtmlAttribute(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }
}

package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

/**
 * Visual regression tests for platform JSON pack templates rendered via {@link TiptapRenderer}.
 *
 * <p>Each test loads a real template JSON from the classpath, builds a context with known test
 * data, renders via TiptapRenderer, and asserts the output HTML contains expected structural
 * elements (headings, resolved variables, bold labels, paragraphs).
 */
class VisualRegressionTest {

  private TiptapRenderer renderer;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    renderer = new TiptapRenderer("body { font-size: 11pt; }");
    objectMapper = new ObjectMapper();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadPackTemplate(String name) throws IOException {
    var resource = new ClassPathResource("template-packs/common/" + name + ".json");
    var json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return objectMapper.readValue(json, Map.class);
  }

  @Test
  void engagementLetter_rendersExpectedStructure() throws IOException {
    var doc = loadPackTemplate("engagement-letter");

    var context = new HashMap<String, Object>();
    context.put("org", Map.of("name", "Acme Corp", "documentFooterText", "Confidential"));
    context.put("customer", Map.of("name", "Jane Smith"));
    context.put(
        "project",
        Map.of(
            "name", "Website Redesign",
            "status", "IN_PROGRESS",
            "description", "Full redesign of corporate website"));

    String html = renderer.render(doc, context, Map.of(), null);

    // Full HTML document structure
    assertThat(html).startsWith("<!DOCTYPE html>");
    assertThat(html).contains("<html>");
    assertThat(html).contains("</body></html>");

    // Heading with org name variable resolved
    assertThat(html).contains("<h1>Acme Corp</h1>");
    // Static heading
    assertThat(html).contains("<h2>Engagement Letter</h2>");
    // Customer greeting
    assertThat(html).contains("Dear ");
    assertThat(html).contains("Jane Smith");
    // Project name resolved
    assertThat(html).contains("Website Redesign");
    // Bold labels
    assertThat(html).contains("<strong>Project:</strong>");
    assertThat(html).contains("<strong>Status:</strong>");
    assertThat(html).contains("<strong>Description:</strong>");
    // Variable values
    assertThat(html).contains("IN_PROGRESS");
    assertThat(html).contains("Full redesign of corporate website");
    // Footer
    assertThat(html).contains("Confidential");
  }

  @Test
  void projectSummary_rendersExpectedStructure() throws IOException {
    var doc = loadPackTemplate("project-summary");

    var context = new HashMap<String, Object>();
    context.put(
        "org", Map.of("name", "Legal Partners LLP", "documentFooterText", "Internal Use Only"));
    context.put("customer", Map.of("name", "BigCo Inc"));
    context.put(
        "project",
        Map.of(
            "name", "Due Diligence Review",
            "status", "ACTIVE",
            "description", "Comprehensive review of acquisition target",
            "lead", "Alice Johnson"));

    String html = renderer.render(doc, context, Map.of(), null);

    // Full HTML document
    assertThat(html).startsWith("<!DOCTYPE html>");

    // Heading with org name
    assertThat(html).contains("<h1>Legal Partners LLP</h1>");
    // Static heading
    assertThat(html).contains("<h2>Project Summary Report</h2>");
    // Project name in h3
    assertThat(html).contains("<h3>Due Diligence Review</h3>");
    // Customer
    assertThat(html).contains("<strong>Customer:</strong>");
    assertThat(html).contains("BigCo Inc");
    // Status
    assertThat(html).contains("<strong>Status:</strong>");
    assertThat(html).contains("ACTIVE");
    // Description
    assertThat(html).contains("Comprehensive review of acquisition target");
    // Lead
    assertThat(html).contains("<strong>Project Lead:</strong>");
    assertThat(html).contains("Alice Johnson");
    // Footer
    assertThat(html).contains("Internal Use Only");
  }

  @Test
  void invoiceCoverLetter_rendersExpectedStructure() throws IOException {
    var doc = loadPackTemplate("invoice-cover-letter");

    var context = new HashMap<String, Object>();
    context.put(
        "org",
        Map.of("name", "Consulting Group SA", "documentFooterText", "Thank you for your business"));
    context.put("customer", Map.of("name", "Client Corp"));
    context.put("invoice", Map.of("number", "INV-2026-001", "total", "R 15,000.00"));

    String html = renderer.render(doc, context, Map.of(), null);

    // Full HTML document
    assertThat(html).startsWith("<!DOCTYPE html>");

    // Heading with org name
    assertThat(html).contains("<h1>Consulting Group SA</h1>");
    // Static heading
    assertThat(html).contains("<h2>Invoice Cover Letter</h2>");
    // Customer greeting
    assertThat(html).contains("Dear ");
    assertThat(html).contains("Client Corp");
    // Static body text
    assertThat(html).contains("Please find attached the invoice for services rendered.");
    // Invoice number
    assertThat(html).contains("<strong>Invoice Number:</strong>");
    assertThat(html).contains("INV-2026-001");
    // Total amount
    assertThat(html).contains("<strong>Total Amount:</strong>");
    assertThat(html).contains("R 15,000.00");
    // Closing text
    assertThat(html).contains("Thank you for your continued business.");
    // Footer
    assertThat(html).contains("Thank you for your business");
  }
}

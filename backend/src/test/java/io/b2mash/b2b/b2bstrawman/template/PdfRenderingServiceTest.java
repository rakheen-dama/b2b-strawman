package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.clause.Clause;
import io.b2mash.b2b.b2bstrawman.clause.ClauseRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PdfRenderingServiceTest {

  private static final Map<String, Object> CONTENT =
      TestDocumentBuilder.doc()
          .heading(1, "Project Report")
          .variable("project.name")
          .paragraph("This is a test document.")
          .build();
  private static final String ORG_ID = "org_pdf_render_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private PdfRenderingService pdfRenderingService;
  @Autowired private DocumentTemplateRepository documentTemplateRepository;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private ClauseRepository clauseRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSchemaMappingRepository orgSchemaMappingRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private OrgSettingsRepository orgSettingsRepository;

  private String tenantSchema;
  private UUID memberIdOwner;
  private UUID testProjectId;
  private UUID testTemplateId;
  private UUID clauseTemplateId;
  private UUID testClauseId;
  private UUID loopTableTemplateId;

  @BeforeAll
  void setup() throws Exception {
    provisioningService.provisionTenant(ORG_ID, "PDF Render Test Org", null);

    memberIdOwner =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_pdf_owner", "pdf_owner@test.com", "PDF Owner", "owner"));

    tenantSchema =
        orgSchemaMappingRepository.findByClerkOrgId(ORG_ID).orElseThrow().getSchemaName();

    // Create test project, templates, and clauses within tenant
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var project =
                      new Project(
                          "Rendering Test Project", "A project for PDF tests", memberIdOwner);
                  project = projectRepository.save(project);
                  testProjectId = project.getId();

                  // Template with variable nodes
                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Test PDF Template",
                          "test-pdf-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          CONTENT);
                  template.setCss("h1 { color: blue; }");
                  template = documentTemplateRepository.save(template);
                  testTemplateId = template.getId();

                  // Create a clause with Tiptap JSON body
                  var clauseBody =
                      TestDocumentBuilder.doc()
                          .paragraph("This clause governs the engagement terms.")
                          .build();
                  var clause = new Clause("Test Clause", "test-clause", clauseBody, "general");
                  clause = clauseRepository.save(clause);
                  testClauseId = clause.getId();

                  // Template with clauseBlock node
                  var clauseContent =
                      TestDocumentBuilder.doc()
                          .heading(1, "Agreement")
                          .clauseBlock(clause.getId(), "test-clause", "Test Clause", true)
                          .build();
                  var clauseTemplate =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Clause Template",
                          "clause-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          clauseContent);
                  clauseTemplate = documentTemplateRepository.save(clauseTemplate);
                  clauseTemplateId = clauseTemplate.getId();

                  // Template with loopTable node
                  var loopContent =
                      TestDocumentBuilder.doc()
                          .heading(1, "Invoice Lines")
                          .loopTable(
                              "invoice.lines",
                              List.of(
                                  Map.of("header", "Description", "key", "description"),
                                  Map.of("header", "Amount", "key", "amount")))
                          .build();
                  var loopTemplate =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Loop Template",
                          "loop-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          loopContent);
                  loopTemplate = documentTemplateRepository.save(loopTemplate);
                  loopTableTemplateId = loopTemplate.getId();
                }));
  }

  @Test
  void generatesPdfWithNonZeroBytes() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var result =
                      pdfRenderingService.generatePdf(testTemplateId, testProjectId, memberIdOwner);

                  assertThat(result).isNotNull();
                  assertThat(result.pdfBytes()).isNotEmpty();
                  // PDF magic bytes: %PDF-
                  assertThat(result.pdfBytes()[0]).isEqualTo((byte) '%');
                  assertThat(result.pdfBytes()[1]).isEqualTo((byte) 'P');
                  assertThat(result.pdfBytes()[2]).isEqualTo((byte) 'D');
                  assertThat(result.pdfBytes()[3]).isEqualTo((byte) 'F');
                }));
  }

  @Test
  void htmlPreviewContainsEntityData() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var result =
                      pdfRenderingService.generatePdf(testTemplateId, testProjectId, memberIdOwner);

                  assertThat(result.htmlPreview()).contains("Rendering Test Project");
                  assertThat(result.htmlPreview()).contains("<!DOCTYPE html>");
                  assertThat(result.htmlPreview()).contains("<style>");
                }));
  }

  @Test
  void cssMergingIncludesBothDefaultAndCustom() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var result =
                      pdfRenderingService.generatePdf(testTemplateId, testProjectId, memberIdOwner);

                  // Default CSS includes @page rule
                  assertThat(result.htmlPreview()).contains("@page");
                  // Custom CSS from template
                  assertThat(result.htmlPreview()).contains("color: blue;");
                }));
  }

  @Test
  void filenameMatchesExpectedPattern() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var result =
                      pdfRenderingService.generatePdf(testTemplateId, testProjectId, memberIdOwner);

                  // Pattern: {slug}-{entity-name-slugified}-{yyyy-MM-dd}.pdf
                  assertThat(result.fileName()).startsWith("test-pdf-template-");
                  assertThat(result.fileName()).contains("rendering-test-project");
                  assertThat(result.fileName()).endsWith(LocalDate.now() + ".pdf");
                }));
  }

  @Test
  void rendersVariableNodesInHtml() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var html =
                      pdfRenderingService.previewHtml(testTemplateId, testProjectId, memberIdOwner);

                  assertThat(html).contains("Rendering Test Project");
                  assertThat(html).contains("<p>");
                  assertThat(html).contains("<h1>");
                  assertThat(html).contains("Project Report");
                }));
  }

  @Test
  void rendersClauseBlockNodesInHtml() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var clause = clauseRepository.findById(testClauseId).orElseThrow();
                  var html =
                      pdfRenderingService.previewHtml(
                          clauseTemplateId, testProjectId, memberIdOwner, List.of(clause));

                  assertThat(html).contains("Agreement");
                  assertThat(html).contains("clause-block");
                  assertThat(html).contains("test-clause");
                  assertThat(html).contains("This clause governs the engagement terms.");
                }));
  }

  @Test
  void rendersLoopTableNodesInHtml() {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  // The loopTable references invoice.lines which won't be in a PROJECT
                  // context, so the table will render with headers but no rows.
                  // This verifies the loopTable node type renders correctly.
                  var html =
                      pdfRenderingService.previewHtml(
                          loopTableTemplateId, testProjectId, memberIdOwner);

                  assertThat(html).contains("<table>");
                  assertThat(html).contains("<th>Description</th>");
                  assertThat(html).contains("<th>Amount</th>");
                  assertThat(html).contains("<thead>");
                  assertThat(html).contains("<tbody>");
                }));
  }

  // --- sanitizeForXhtml (622A middot regression: HtmlUtils.htmlEscape emits HTML4 named entities
  // such as &middot; / &eacute; that OpenHTMLToPDF's XML parser does not declare) ---

  @Test
  void sanitizeForXhtml_convertsMiddotAndAccentedEntitiesToNumericRefs() {
    assertThat(pdfRenderingService.sanitizeForXhtml("a &middot; b")).isEqualTo("a &#183; b");
    assertThat(pdfRenderingService.sanitizeForXhtml("Jos&eacute;")).isEqualTo("Jos&#233;");
    assertThat(pdfRenderingService.sanitizeForXhtml("M&uuml;ller &times; &copy;"))
        .isEqualTo("M&#252;ller &#215; &#169;");
  }

  @Test
  void sanitizeForXhtml_previouslyMappedEntitiesKeepTheirNumericOutput() {
    assertThat(
            pdfRenderingService.sanitizeForXhtml(
                "&mdash;&ndash;&nbsp;&hellip;&bull;&lsquo;&rsquo;&ldquo;&rdquo;"))
        .isEqualTo("&#8212;&#8211;&#160;&#8230;&#8226;&#8216;&#8217;&#8220;&#8221;");
  }

  @Test
  void sanitizeForXhtml_leavesXmlPredefinedNumericAndUnknownEntitiesUntouched() {
    String xmlPredefined = "&amp; &lt; &gt; &quot; &apos;";
    assertThat(pdfRenderingService.sanitizeForXhtml(xmlPredefined)).isEqualTo(xmlPredefined);
    assertThat(pdfRenderingService.sanitizeForXhtml("&#183; &#x00B7;"))
        .isEqualTo("&#183; &#x00B7;");
    assertThat(pdfRenderingService.sanitizeForXhtml("&notarealentity;"))
        .isEqualTo("&notarealentity;");
    assertThat(pdfRenderingService.sanitizeForXhtml(null)).isNull();
  }

  @Test
  void htmlToPdf_rendersHtmlEscapedMiddotAndAccentWithoutThrowing() {
    byte[] pdf =
        pdfRenderingService.htmlToPdf(
            "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/></head><body>"
                + "<p>2026-10-24 &middot; Jos&eacute; &middot; Drafting</p></body></html>");

    assertThat(pdf).isNotEmpty();
    assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
  }

  /**
   * Phase 88 626B (U8, ADR-353, ruling R-6): the tenant brand colour reaches the PDF itself, not
   * just the HTML. OpenHTMLToPDF has no CSS custom properties, so the only proof is a concrete fill
   * colour in the page content stream. Uses an h2/h3 template with no custom CSS — {@code
   * testTemplateId}'s {@code h1 { color: blue; }} would (correctly) override the brand rule.
   */
  @Test
  void brandColourReachesPdfContentStream() {
    var brandTemplateId = new UUID[1];
    var priorBrand = new String[1];
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var template =
                      new DocumentTemplate(
                          TemplateEntityType.PROJECT,
                          "Brand Colour Template",
                          "brand-colour-template",
                          TemplateCategory.ENGAGEMENT_LETTER,
                          TestDocumentBuilder.doc()
                              .heading(2, "Brand Heading")
                              .heading(3, "Brand Subheading")
                              .paragraph("Body text stays #333.")
                              .build());
                  brandTemplateId[0] = documentTemplateRepository.save(template).getId();
                  priorBrand[0] =
                      orgSettingsRepository
                          .findForCurrentTenant()
                          .orElseThrow()
                          .getBranding()
                          .getBrandColor();
                }));

    try {
      // Default (no brand colour): #1a1a2e headings, no teal fill anywhere.
      setBrandColour(null);
      runInTenant(
          () ->
              transactionTemplate.executeWithoutResult(
                  tx -> {
                    var result =
                        pdfRenderingService.generatePdf(
                            brandTemplateId[0], testProjectId, memberIdOwner);
                    assertThat(result.htmlPreview()).contains("h1, h2, h3 { color: #1a1a2e; }");
                    assertThat(hasFillColour(result.pdfBytes(), 0x1a, 0x1a, 0x2e)).isTrue();
                    assertThat(hasFillColour(result.pdfBytes(), 0x0f, 0x76, 0x6e)).isFalse();
                  }));

      // Brand colour set: the teal fill (#0f766e ~ 0.059 0.463 0.431 rg) is in the content stream.
      setBrandColour("#0f766e");
      runInTenant(
          () ->
              transactionTemplate.executeWithoutResult(
                  tx -> {
                    var result =
                        pdfRenderingService.generatePdf(
                            brandTemplateId[0], testProjectId, memberIdOwner);
                    assertThat(result.htmlPreview()).contains("h1, h2, h3 { color: #0f766e; }");
                    assertThat(hasFillColour(result.pdfBytes(), 0x0f, 0x76, 0x6e)).isTrue();
                  }));
    } finally {
      setBrandColour(priorBrand[0]);
    }
  }

  private void setBrandColour(String hex) {
    runInTenant(
        () ->
            transactionTemplate.executeWithoutResult(
                tx -> {
                  var settings = orgSettingsRepository.findForCurrentTenant().orElseThrow();
                  settings.getBranding().setBrandColor(hex);
                  orgSettingsRepository.save(settings);
                }));
  }

  /** True when any page's content stream sets an {@code rg} fill within 1/255 of (r, g, b). */
  private static boolean hasFillColour(byte[] pdf, int r, int g, int b) {
    try (var doc = Loader.loadPDF(pdf)) {
      for (var page : doc.getPages()) {
        List<Object> tokens = new PDFStreamParser(page).parse();
        for (int i = 3; i < tokens.size(); i++) {
          if (tokens.get(i) instanceof Operator op && op.getName().equals("rg")) {
            int pr = Math.round(((COSNumber) tokens.get(i - 3)).floatValue() * 255);
            int pg = Math.round(((COSNumber) tokens.get(i - 2)).floatValue() * 255);
            int pb = Math.round(((COSNumber) tokens.get(i - 1)).floatValue() * 255);
            if (Math.abs(pr - r) <= 1 && Math.abs(pg - g) <= 1 && Math.abs(pb - b) <= 1) {
              return true;
            }
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return false;
  }

  // --- Helpers ---

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, memberIdOwner)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(action);
  }
}

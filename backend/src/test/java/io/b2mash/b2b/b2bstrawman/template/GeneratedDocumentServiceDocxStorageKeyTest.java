package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.clause.ClauseResolver;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentService;
import io.b2mash.b2b.b2bstrawman.integration.storage.PresignedUrl;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustDataVisibility;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * G88-F33: the DOCX generation path uploads the merged DOCX and, when a PDF is requested and the
 * converter succeeds, a PDF sibling. Both must live under one fresh {@code generated/{uuid}/}
 * segment and differ only by extension, and the record's primary/secondary keys must point at the
 * right object. Plain Mockito unit test — the integration suite has no PDF converter, so this is
 * the only place the PDF sibling key is exercised.
 */
@ExtendWith(MockitoExtension.class)
class GeneratedDocumentServiceDocxStorageKeyTest {

  private static final String TENANT = "tenant_5039f2d497cf";
  private static final String TEMPLATE_DOCX_KEY = "org/" + TENANT + "/templates/tpl/template.docx";
  private static final Pattern GENERATED_KEY =
      Pattern.compile(
          "^org/"
              + TENANT
              + "/generated/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/"
              + "engagement-letter-sipho-dlamini-2026-10-29\\.(docx|pdf)$");

  @Mock private GeneratedDocumentRepository generatedDocumentRepository;
  @Mock private DocumentTemplateRepository documentTemplateRepository;
  @Mock private MemberNameResolver memberNameResolver;
  @Mock private PdfRenderingService pdfRenderingService;
  @Mock private DocumentTemplateService documentTemplateService;
  @Mock private TemplateValidationService templateValidationService;
  @Mock private StorageService storageService;
  @Mock private DocumentRepository documentRepository;
  @Mock private AuditService auditService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private ClauseResolver clauseResolver;
  @Mock private PrerequisiteService prerequisiteService;
  @Mock private DocxMergeService docxMergeService;
  @Mock private PdfConversionService pdfConversionService;
  @Mock private ProjectAccessService projectAccessService;
  @Mock private OrgSettingsRepository orgSettingsRepository;
  @Mock private DocumentService documentService;
  @Mock private TemplateContextBuilder projectContextBuilder;
  @Mock private TrustDataVisibility trustDataVisibility;
  @Mock private TemplateVariableAnalyzer templateVariableAnalyzer;

  private final UUID templateId = UUID.randomUUID();
  private final UUID entityId = UUID.randomUUID();
  private final UUID memberId = UUID.randomUUID();
  private final byte[] mergedDocx = {1, 2, 3};
  private final byte[] convertedPdf = {4, 5, 6, 7};

  private GeneratedDocumentService service;

  @BeforeEach
  void setUp() throws Exception {
    service =
        new GeneratedDocumentService(
            generatedDocumentRepository,
            documentTemplateRepository,
            memberNameResolver,
            pdfRenderingService,
            documentTemplateService,
            templateValidationService,
            storageService,
            documentRepository,
            auditService,
            eventPublisher,
            clauseResolver,
            prerequisiteService,
            docxMergeService,
            pdfConversionService,
            projectAccessService,
            List.of(projectContextBuilder),
            orgSettingsRepository,
            documentService,
            trustDataVisibility,
            templateVariableAnalyzer,
            Clock.fixed(Instant.parse("2026-10-29T08:00:00Z"), ZoneOffset.UTC));

    var template =
        new DocumentTemplate(
            TemplateEntityType.PROJECT,
            "Engagement Letter",
            "engagement-letter",
            TemplateCategory.ENGAGEMENT_LETTER,
            Map.of());
    template.setFormat(TemplateFormat.DOCX);
    template.setDocxS3Key(TEMPLATE_DOCX_KEY);

    when(documentTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));
    when(storageService.download(TEMPLATE_DOCX_KEY)).thenReturn(new byte[] {9});
    when(projectContextBuilder.supports()).thenReturn(TemplateEntityType.PROJECT);
    when(projectContextBuilder.buildContext(entityId, memberId))
        .thenReturn(Map.of("project", Map.of("name", "Sipho Dlamini")));
    when(docxMergeService.merge(any(), any())).thenReturn(mergedDocx);
    when(pdfConversionService.convertToPdf(mergedDocx)).thenReturn(Optional.of(convertedPdf));
    when(storageService.generateDownloadUrl(anyString(), any()))
        .thenAnswer(
            inv -> new PresignedUrl("https://s3.test/" + inv.getArgument(0), Instant.now()));
    when(generatedDocumentRepository.save(any(GeneratedDocument.class)))
        .thenAnswer(
            inv -> {
              GeneratedDocument doc = inv.getArgument(0);
              ReflectionTestUtils.setField(doc, "id", UUID.randomUUID());
              return doc;
            });
  }

  @Test
  void pdfOutput_docxAndPdfShareOneUuidSegment_pdfIsPrimary() {
    generate("PDF");

    var keys = uploadedKeys();
    String docxKey = keys.docx();
    String pdfKey = keys.pdf();
    assertSiblings(docxKey, pdfKey);

    var saved = savedRecord();
    assertThat(saved.getS3Key()).isEqualTo(pdfKey);
    assertThat(saved.getDocxS3Key()).isEqualTo(docxKey);
    assertThat(saved.getOutputFormat()).isEqualTo(OutputFormat.PDF);
  }

  @Test
  void bothOutput_docxAndPdfShareOneUuidSegment_docxIsPrimary() {
    var result = generate("BOTH");

    var keys = uploadedKeys();
    assertSiblings(keys.docx(), keys.pdf());

    var saved = savedRecord();
    assertThat(saved.getS3Key()).isEqualTo(keys.docx());
    assertThat(saved.getDocxS3Key()).isNull();
    assertThat(saved.getOutputFormat()).isEqualTo(OutputFormat.DOCX);
    assertThat(result.downloadUrl()).endsWith(keys.docx());
    assertThat(result.pdfDownloadUrl()).endsWith(keys.pdf());
  }

  @Test
  void twoGenerations_getDistinctUuidSegments() {
    generate("PDF");
    generate("PDF");

    var keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(storageService, times(4)).upload(keyCaptor.capture(), any(byte[].class), anyString());
    var tokens =
        keyCaptor.getAllValues().stream().map(GeneratedDocumentServiceDocxStorageKeyTest::token);
    assertThat(tokens.distinct()).hasSize(2);
  }

  // --- Helpers ---

  private GeneratedDocumentService.GenerateDocxResult generate(String outputFormat) {
    return ScopedValue.where(RequestScopes.TENANT_ID, TENANT)
        .call(() -> service.generateDocx(templateId, entityId, outputFormat, memberId));
  }

  private UploadedKeys uploadedKeys() {
    var keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(storageService)
        .upload(
            keyCaptor.capture(),
            eq(mergedDocx),
            eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    String docxKey = keyCaptor.getValue();
    verify(storageService).upload(keyCaptor.capture(), eq(convertedPdf), eq("application/pdf"));
    String pdfKey = keyCaptor.getValue();
    return new UploadedKeys(docxKey, pdfKey);
  }

  private GeneratedDocument savedRecord() {
    var captor = ArgumentCaptor.forClass(GeneratedDocument.class);
    verify(generatedDocumentRepository).save(captor.capture());
    return captor.getValue();
  }

  private static void assertSiblings(String docxKey, String pdfKey) {
    assertThat(docxKey).matches(GENERATED_KEY).endsWith(".docx");
    assertThat(pdfKey).matches(GENERATED_KEY).endsWith(".pdf");
    assertThat(token(pdfKey)).isEqualTo(token(docxKey));
    assertThat(pdfKey.substring(0, pdfKey.length() - ".pdf".length()))
        .isEqualTo(docxKey.substring(0, docxKey.length() - ".docx".length()));
  }

  private static String token(String key) {
    var matcher = GENERATED_KEY.matcher(key);
    assertThat(matcher.matches()).as("generated key %s", key).isTrue();
    return matcher.group(1);
  }

  private record UploadedKeys(String docx, String pdf) {}
}

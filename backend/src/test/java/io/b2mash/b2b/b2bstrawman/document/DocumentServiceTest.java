package io.b2mash.b2b.b2bstrawman.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccess;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService.PresignedDownloadResult;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService.PresignedUploadResult;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

  @Mock private DocumentRepository documentRepository;
  @Mock private ProjectAccessService projectAccessService;
  @Mock private S3PresignedUrlService s3Service;
  @InjectMocks private DocumentService service;

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID MEMBER_ID = UUID.randomUUID();
  private static final String ORG_ID = "org_test";
  private static final String ORG_ROLE = "member";
  private static final ProjectAccess GRANTED = new ProjectAccess(true, true, true, false, "member");

  @Test
  void listDocuments_returnsDocumentsForExistingProject() {
    var doc = new Document(PROJECT_ID, "file.pdf", "application/pdf", 1024, MEMBER_ID);
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenReturn(GRANTED);
    when(documentRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(doc));

    var result = service.listDocuments(PROJECT_ID, MEMBER_ID, ORG_ROLE);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getFileName()).isEqualTo("file.pdf");
  }

  @Test
  void listDocuments_throwsForMissingProject() {
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenThrow(new ResourceNotFoundException("Project", PROJECT_ID));

    assertThatThrownBy(() -> service.listDocuments(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(documentRepository, never()).findByProjectId(any());
  }

  @Test
  void listDocuments_throwsWhenAccessDenied() {
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenThrow(new ResourceNotFoundException("Project", PROJECT_ID));

    assertThatThrownBy(() -> service.listDocuments(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(documentRepository, never()).findByProjectId(any());
  }

  @Test
  void initiateUpload_createsDocumentAndGeneratesPresignedUrl() throws Exception {
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenReturn(GRANTED);

    // Simulate JPA ID generation: set the id field via reflection on save
    when(documentRepository.save(any(Document.class)))
        .thenAnswer(
            invocation -> {
              Document doc = invocation.getArgument(0);
              if (doc.getId() == null) {
                Field idField = Document.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(doc, UUID.randomUUID());
              }
              return doc;
            });

    when(s3Service.generateUploadUrl(
            eq(ORG_ID), eq(PROJECT_ID.toString()), any(), eq("application/pdf")))
        .thenReturn(
            new PresignedUploadResult("https://s3.example.com/upload", "org/test/key", 3600));

    var result =
        service.initiateUpload(
            PROJECT_ID, "doc.pdf", "application/pdf", 5000, ORG_ID, MEMBER_ID, ORG_ROLE);

    assertThat(result.presignedUrl()).isEqualTo("https://s3.example.com/upload");
    assertThat(result.expiresInSeconds()).isEqualTo(3600);
    assertThat(result.documentId()).isNotNull();

    // Verify document saved twice: initial creation + S3 key assignment
    var captor = ArgumentCaptor.forClass(Document.class);
    verify(documentRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues().getLast().getS3Key()).isEqualTo("org/test/key");
  }

  @Test
  void initiateUpload_throwsForMissingProject() {
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenThrow(new ResourceNotFoundException("Project", PROJECT_ID));

    assertThatThrownBy(
            () ->
                service.initiateUpload(
                    PROJECT_ID, "doc.pdf", "application/pdf", 5000, ORG_ID, MEMBER_ID, ORG_ROLE))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(documentRepository, never()).save(any());
  }

  @Test
  void initiateUpload_throwsWhenAccessDenied() {
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenThrow(new ResourceNotFoundException("Project", PROJECT_ID));

    assertThatThrownBy(
            () ->
                service.initiateUpload(
                    PROJECT_ID, "doc.pdf", "application/pdf", 5000, ORG_ID, MEMBER_ID, ORG_ROLE))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(documentRepository, never()).save(any());
    verify(s3Service, never()).generateUploadUrl(any(), any(), any(), any());
  }

  @Test
  void confirmUpload_transitionsPendingToUploaded() {
    var docId = UUID.randomUUID();
    var doc = new Document(PROJECT_ID, "file.pdf", "application/pdf", 1024, MEMBER_ID);
    assertThat(doc.getStatus()).isEqualTo(Document.Status.PENDING);

    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenReturn(GRANTED);
    when(documentRepository.save(doc)).thenReturn(doc);

    var result = service.confirmUpload(docId, MEMBER_ID, ORG_ROLE);

    assertThat(result.getStatus()).isEqualTo(Document.Status.UPLOADED);
    verify(documentRepository).save(doc);
  }

  @Test
  void confirmUpload_isIdempotentForAlreadyUploadedDocument() {
    var docId = UUID.randomUUID();
    var doc = new Document(PROJECT_ID, "file.pdf", "application/pdf", 1024, MEMBER_ID);
    doc.confirmUpload(); // already UPLOADED

    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenReturn(GRANTED);

    var result = service.confirmUpload(docId, MEMBER_ID, ORG_ROLE);

    assertThat(result.getStatus()).isEqualTo(Document.Status.UPLOADED);
    verify(documentRepository, never()).save(any());
  }

  @Test
  void confirmUpload_throwsForUnknownDocument() {
    var docId = UUID.randomUUID();
    when(documentRepository.findById(docId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.confirmUpload(docId, MEMBER_ID, ORG_ROLE))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void confirmUpload_throwsWhenAccessDenied() {
    var docId = UUID.randomUUID();
    var doc = new Document(PROJECT_ID, "file.pdf", "application/pdf", 1024, MEMBER_ID);

    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenThrow(new ResourceNotFoundException("Project", PROJECT_ID));

    assertThatThrownBy(() -> service.confirmUpload(docId, MEMBER_ID, ORG_ROLE))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(documentRepository, never()).save(any());
  }

  @Test
  void cancelUpload_deletesDocumentWhenPending() {
    var docId = UUID.randomUUID();
    var doc = new Document(PROJECT_ID, "file.pdf", "application/pdf", 1024, MEMBER_ID);

    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenReturn(GRANTED);

    service.cancelUpload(docId, MEMBER_ID, ORG_ROLE);

    verify(documentRepository).delete(doc);
  }

  @Test
  void cancelUpload_throwsConflictForUploadedDocument() {
    var docId = UUID.randomUUID();
    var doc = new Document(PROJECT_ID, "file.pdf", "application/pdf", 1024, MEMBER_ID);
    doc.confirmUpload(); // status is UPLOADED

    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenReturn(GRANTED);

    assertThatThrownBy(() -> service.cancelUpload(docId, MEMBER_ID, ORG_ROLE))
        .isInstanceOf(ResourceConflictException.class);
    verify(documentRepository, never()).delete(any());
  }

  @Test
  void cancelUpload_throwsWhenAccessDenied() {
    var docId = UUID.randomUUID();
    var doc = new Document(PROJECT_ID, "file.pdf", "application/pdf", 1024, MEMBER_ID);

    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenThrow(new ResourceNotFoundException("Project", PROJECT_ID));

    assertThatThrownBy(() -> service.cancelUpload(docId, MEMBER_ID, ORG_ROLE))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(documentRepository, never()).delete(any());
  }

  @Test
  void cancelUpload_throwsForUnknownDocument() {
    var docId = UUID.randomUUID();
    when(documentRepository.findById(docId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.cancelUpload(docId, MEMBER_ID, ORG_ROLE))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void getPresignedDownloadUrl_returnsUrlForUploadedDocument() {
    var docId = UUID.randomUUID();
    var doc = new Document(PROJECT_ID, "file.pdf", "application/pdf", 1024, MEMBER_ID);
    doc.assignS3Key("org/test/project/123/abc");
    doc.confirmUpload();

    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenReturn(GRANTED);
    when(s3Service.generateDownloadUrl("org/test/project/123/abc"))
        .thenReturn(new PresignedDownloadResult("https://s3.example.com/download", 3600));

    var result = service.getPresignedDownloadUrl(docId, MEMBER_ID, ORG_ROLE);

    assertThat(result.url()).isEqualTo("https://s3.example.com/download");
  }

  @Test
  void getPresignedDownloadUrl_throwsForPendingDocument() {
    var docId = UUID.randomUUID();
    var doc = new Document(PROJECT_ID, "file.pdf", "application/pdf", 1024, MEMBER_ID);
    // status is PENDING

    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenReturn(GRANTED);

    assertThatThrownBy(() -> service.getPresignedDownloadUrl(docId, MEMBER_ID, ORG_ROLE))
        .isInstanceOf(InvalidStateException.class);
    verify(s3Service, never()).generateDownloadUrl(any());
  }

  @Test
  void getPresignedDownloadUrl_throwsForUnknownDocument() {
    var docId = UUID.randomUUID();
    when(documentRepository.findById(docId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPresignedDownloadUrl(docId, MEMBER_ID, ORG_ROLE))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void getPresignedDownloadUrl_throwsWhenAccessDenied() {
    var docId = UUID.randomUUID();
    var doc = new Document(PROJECT_ID, "file.pdf", "application/pdf", 1024, MEMBER_ID);
    doc.assignS3Key("org/test/project/123/abc");
    doc.confirmUpload();

    when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
    when(projectAccessService.requireViewAccess(PROJECT_ID, MEMBER_ID, ORG_ROLE))
        .thenThrow(new ResourceNotFoundException("Project", PROJECT_ID));

    assertThatThrownBy(() -> service.getPresignedDownloadUrl(docId, MEMBER_ID, ORG_ROLE))
        .isInstanceOf(ResourceNotFoundException.class);
    verify(s3Service, never()).generateDownloadUrl(any());
  }
}

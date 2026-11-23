package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnBatchResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnBatchSummaryResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnDatasetResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnMappingRequest;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnPositionRequest;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnPreviewResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnRowResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnSheetRequest;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnUploadResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code /api/take-on} (Phase 93 653A, §17.4.1 minus the 655A+ rows). {@code MANAGE_TAKE_ON} on
 * every method; the dataset's own capability is enforced in the services by {@link
 * TakeOnAccessPolicy} after the batch is loaded (404 before 403). One service call per method.
 */
@RestController
public class TakeOnController {

  private final TakeOnBatchService batchService;
  private final TakeOnBatchQueryService queryService;

  public TakeOnController(TakeOnBatchService batchService, TakeOnBatchQueryService queryService) {
    this.batchService = batchService;
    this.queryService = queryService;
  }

  @GetMapping("/api/take-on/datasets")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<List<TakeOnDatasetResponse>> listDatasets() {
    return ResponseEntity.ok(queryService.listDatasets());
  }

  @GetMapping("/api/take-on/templates/{dataset}")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<byte[]> template(
      @PathVariable TakeOnDataset dataset, @RequestParam(required = false) TakeOnSection section) {
    var file = queryService.template(dataset, section);
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .body(file.bytes());
  }

  @PostMapping(value = "/api/take-on/batches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<TakeOnUploadResponse> createBatch(
      @RequestParam("file") MultipartFile file,
      @RequestParam TakeOnDataset dataset,
      @RequestParam(required = false) TakeOnSection section,
      @RequestParam(required = false) UUID trustAccountId,
      @RequestParam(required = false) String name) {
    var created = batchService.createBatch(file, dataset, section, trustAccountId, name);
    return ResponseEntity.created(URI.create("/api/take-on/batches/" + created.batch().id()))
        .body(created);
  }

  @GetMapping("/api/take-on/batches")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<Page<TakeOnBatchSummaryResponse>> listBatches(
      @RequestParam(required = false) TakeOnDataset dataset,
      @RequestParam(required = false) TakeOnBatchStatus status,
      @PageableDefault(size = 50) Pageable pageable) {
    return ResponseEntity.ok(queryService.listBatches(dataset, status, pageable));
  }

  @GetMapping("/api/take-on/batches/{batchId}")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<TakeOnBatchResponse> getBatch(@PathVariable UUID batchId) {
    return ResponseEntity.ok(queryService.getBatch(batchId));
  }

  @PostMapping(
      value = "/api/take-on/batches/{batchId}/sources",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<TakeOnUploadResponse> addSource(
      @PathVariable UUID batchId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) TakeOnSection section) {
    return ResponseEntity.status(201).body(batchService.addSource(batchId, file, section));
  }

  @DeleteMapping("/api/take-on/batches/{batchId}/sources/{sourceId}")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<TakeOnBatchResponse> removeSource(
      @PathVariable UUID batchId, @PathVariable UUID sourceId) {
    return ResponseEntity.ok(batchService.removeSource(batchId, sourceId));
  }

  @PutMapping("/api/take-on/batches/{batchId}/sources/{sourceId}/sheet")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<TakeOnUploadResponse> selectSheet(
      @PathVariable UUID batchId,
      @PathVariable UUID sourceId,
      @Valid @RequestBody TakeOnSheetRequest request) {
    return ResponseEntity.ok(batchService.selectSheet(batchId, sourceId, request));
  }

  @GetMapping("/api/take-on/batches/{batchId}/sources/{sourceId}/preview")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<TakeOnPreviewResponse> preview(
      @PathVariable UUID batchId,
      @PathVariable UUID sourceId,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(queryService.preview(batchId, sourceId, limit));
  }

  @PutMapping("/api/take-on/batches/{batchId}/sources/{sourceId}/mapping")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<TakeOnBatchResponse> saveMapping(
      @PathVariable UUID batchId,
      @PathVariable UUID sourceId,
      @RequestBody TakeOnMappingRequest request) {
    return ResponseEntity.ok(batchService.saveMapping(batchId, sourceId, request));
  }

  @GetMapping("/api/take-on/batches/{batchId}/sources/{sourceId}/file")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<byte[]> downloadFile(
      @PathVariable UUID batchId, @PathVariable UUID sourceId) {
    var file = queryService.downloadFile(batchId, sourceId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(file.fileName()).build().toString())
        .body(file.bytes());
  }

  @PutMapping("/api/take-on/batches/{batchId}/position")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<TakeOnBatchResponse> savePosition(
      @PathVariable UUID batchId, @Valid @RequestBody TakeOnPositionRequest request) {
    return ResponseEntity.ok(batchService.savePosition(batchId, request));
  }

  @PostMapping("/api/take-on/batches/{batchId}/validate")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<TakeOnBatchResponse> validate(@PathVariable UUID batchId) {
    return ResponseEntity.ok(batchService.validate(batchId));
  }

  @GetMapping("/api/take-on/batches/{batchId}/rows")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<Page<TakeOnRowResponse>> listRows(
      @PathVariable UUID batchId,
      @RequestParam(required = false) TakeOnRowStatus status,
      @RequestParam(required = false) TakeOnSection section,
      @RequestParam(defaultValue = "false") boolean blocksOnly,
      @PageableDefault(size = 50) Pageable pageable) {
    return ResponseEntity.ok(queryService.listRows(batchId, status, section, blocksOnly, pageable));
  }

  @PostMapping("/api/take-on/batches/{batchId}/cancel")
  @RequiresCapability("MANAGE_TAKE_ON")
  public ResponseEntity<TakeOnBatchResponse> cancel(@PathVariable UUID batchId) {
    return ResponseEntity.ok(batchService.cancel(batchId));
  }
}

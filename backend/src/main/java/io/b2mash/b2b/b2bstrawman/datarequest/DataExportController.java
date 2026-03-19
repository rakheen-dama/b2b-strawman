package io.b2mash.b2b.b2bstrawman.datarequest;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DataExportController {

  private final DataExportService dataExportService;

  public DataExportController(DataExportService dataExportService) {
    this.dataExportService = dataExportService;
  }

  @PostMapping("/api/customers/{customerId}/data-export")
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<ExportResult> triggerExport(@PathVariable UUID customerId) {
    return ResponseEntity.accepted()
        .body(dataExportService.triggerCustomerExport(customerId, RequestScopes.requireMemberId()));
  }

  @GetMapping("/api/data-exports/{exportId}")
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<ExportStatusResponse> getExportStatus(@PathVariable UUID exportId) {
    return ResponseEntity.ok(dataExportService.getExportStatus(exportId));
  }

  @GetMapping("/api/data-exports")
  @RequiresCapability("MANAGE_COMPLIANCE")
  public ResponseEntity<List<ExportStatusResponse>> listExports() {
    return ResponseEntity.ok(dataExportService.listExports());
  }
}

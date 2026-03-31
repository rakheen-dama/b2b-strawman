package io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.AdversePartyService.AdversePartyLinkResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.AdversePartyService.AdversePartyResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.AdversePartyService.CreateAdversePartyRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.AdversePartyService.LinkRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.conflictcheck.AdversePartyService.UpdateAdversePartyRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AdversePartyController {

  private final AdversePartyService adversePartyService;

  public AdversePartyController(AdversePartyService adversePartyService) {
    this.adversePartyService = adversePartyService;
  }

  @GetMapping("/adverse-parties")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<Page<AdversePartyResponse>> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String partyType,
      Pageable pageable) {
    return ResponseEntity.ok(adversePartyService.list(search, partyType, pageable));
  }

  @GetMapping("/adverse-parties/{id}")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<AdversePartyResponse> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(adversePartyService.getById(id));
  }

  @PostMapping("/adverse-parties")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<AdversePartyResponse> create(
      @Valid @RequestBody CreateAdversePartyRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(adversePartyService.create(request));
  }

  @PutMapping("/adverse-parties/{id}")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<AdversePartyResponse> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateAdversePartyRequest request) {
    return ResponseEntity.ok(adversePartyService.update(id, request));
  }

  @DeleteMapping("/adverse-parties/{id}")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    adversePartyService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/adverse-parties/{id}/links")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<AdversePartyLinkResponse> link(
      @PathVariable UUID id, @Valid @RequestBody LinkRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(adversePartyService.link(id, request));
  }

  @DeleteMapping("/adverse-party-links/{linkId}")
  @RequiresCapability("MANAGE_LEGAL")
  public ResponseEntity<Void> unlink(@PathVariable UUID linkId) {
    adversePartyService.unlink(linkId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/projects/{id}/adverse-parties")
  @RequiresCapability("VIEW_LEGAL")
  public ResponseEntity<List<AdversePartyLinkResponse>> listForProject(@PathVariable UUID id) {
    return ResponseEntity.ok(adversePartyService.listForProject(id));
  }
}

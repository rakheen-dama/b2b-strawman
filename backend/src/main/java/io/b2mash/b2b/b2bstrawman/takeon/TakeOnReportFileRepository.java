package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The rendered files of the take-on reports (Phase 93 657D). */
public interface TakeOnReportFileRepository extends JpaRepository<TakeOnReportFile, UUID> {

  List<TakeOnReportFile> findByReportId(UUID reportId);

  List<TakeOnReportFile> findByReportIdIn(java.util.Collection<UUID> reportIds);

  Optional<TakeOnReportFile> findByReportIdAndFormat(UUID reportId, TakeOnReportFile.Format format);

  boolean existsByReportIdAndFormat(UUID reportId, TakeOnReportFile.Format format);
}

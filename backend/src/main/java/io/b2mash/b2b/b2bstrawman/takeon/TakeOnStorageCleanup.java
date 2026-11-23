package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable deletion of take-on files in S3 (§17.10 X4). A purged source keeps its key until the
 * object is gone: the after-commit delete forgets the key in a new short transaction only when the
 * delete succeeded, and the abandoned-batch job retries every purged source still holding a key. An
 * upload whose transaction rolls back deletes the object it stored. Runs in the caller's tenant
 * scope; never logs file contents.
 */
@Component
public class TakeOnStorageCleanup {

  private static final Logger log = LoggerFactory.getLogger(TakeOnStorageCleanup.class);

  private final StorageService storageService;
  private final TakeOnSourceRepository sources;
  private final TransactionTemplate newTransaction;

  public TakeOnStorageCleanup(
      StorageService storageService,
      TakeOnSourceRepository sources,
      PlatformTransactionManager transactionManager) {
    this.storageService = storageService;
    this.sources = sources;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** After commit: deletes each purged source's object and, once gone, forgets its key. */
  public void deletePurgedAfterCommit(Map<UUID, String> keysBySource) {
    if (keysBySource.isEmpty()) {
      return;
    }
    afterCommit(() -> keysBySource.forEach(this::deletePurged));
  }

  /** After commit: deletes objects no source row refers to any more (a removed file). */
  public void deleteAfterCommit(List<String> keys) {
    if (keys.isEmpty()) {
      return;
    }
    afterCommit(() -> keys.forEach(storageService::delete));
  }

  /** The upload's transaction rolled back: the object it stored belongs to no source. */
  public void deleteOnRollback(String key) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) {
              storageService.delete(key);
            }
          }
        });
  }

  /**
   * Retries the delete of every purged source of the current tenant that still holds a key; returns
   * how many objects are now gone.
   */
  public int retryPurgedDeletes() {
    int deleted = 0;
    for (TakeOnSource source : sources.findByPurgedAtIsNotNullAndS3KeyIsNotNull()) {
      if (deletePurged(source.getId(), source.getS3Key())) {
        deleted++;
      }
    }
    return deleted;
  }

  private boolean deletePurged(UUID sourceId, String key) {
    if (!storageService.tryDelete(key)) {
      log.warn("Take-on file delete failed for source {}; retried by the purge job", sourceId);
      return false;
    }
    try {
      newTransaction.executeWithoutResult(status -> sources.forgetStorageKey(sourceId, key));
      return true;
    } catch (RuntimeException e) {
      // the object is gone; a stale key is retried (deleting a missing object succeeds)
      log.warn("Take-on file key not cleared for source {}: {}", sourceId, e.getMessage());
      return false;
    }
  }

  private static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }
}

package io.b2mash.b2b.b2bstrawman.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.testutil.InMemoryStorageService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 653A review: take-on file deletes are durable — a failed delete keeps the key for a retry. */
class TakeOnStorageCleanupTest {

  private static final String KEY = "org/tenant_x/take-on/b/clients.csv";

  /** The in-memory store, with deletes that fail while {@code failing} is set. */
  private static final class FlakyStorage extends InMemoryStorageService {
    boolean failing;

    @Override
    public void delete(String key) {
      if (failing) {
        throw new IllegalStateException("S3 unavailable");
      }
      super.delete(key);
    }
  }

  private final FlakyStorage storage = new FlakyStorage();
  private final TakeOnSourceRepository sources = mock(TakeOnSourceRepository.class);
  private final TakeOnStorageCleanup cleanup =
      new TakeOnStorageCleanup(storage, sources, mock(PlatformTransactionManager.class));

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void aPurgedSourceKeepsItsKeyUntilTheDeleteSucceeds() {
    var source = source();
    source.attachStorageKey(KEY);
    assertThat(source.purge()).isEqualTo(KEY);
    assertThat(source.isPurged()).isTrue();
    assertThat(source.getS3Key()).as("kept until the object is gone").isEqualTo(KEY);
    assertThat(source.purge()).as("purged once").isNull();
  }

  @Test
  void aFailedDeleteKeepsTheKeyAndTheRetryForgetsItOnceTheObjectIsGone() {
    var id = UUID.randomUUID();
    storage.upload(KEY, new byte[] {1}, "text/csv");
    storage.failing = true;

    cleanup.deletePurgedAfterCommit(Map.of(id, KEY)); // no transaction: runs now
    assertThat(storage.listKeys(KEY)).containsExactly(KEY);
    verify(sources, never()).forgetStorageKey(id, KEY);

    var purged = source();
    purged.attachStorageKey(KEY);
    purged.purge();
    when(sources.findByPurgedAtIsNotNullAndS3KeyIsNotNull()).thenReturn(List.of(purged));
    assertThat(cleanup.retryPurgedDeletes()).isZero();

    storage.failing = false;
    assertThat(cleanup.retryPurgedDeletes()).isEqualTo(1);
    assertThat(storage.listKeys(KEY)).isEmpty();
    verify(sources).forgetStorageKey(purged.getId(), KEY);
  }

  @Test
  void anUploadWhoseTransactionRollsBackDeletesItsObject() {
    storage.upload(KEY, new byte[] {1}, "text/csv");
    TransactionSynchronizationManager.initSynchronization();
    cleanup.deleteOnRollback(KEY);
    var synchronizations = TransactionSynchronizationManager.getSynchronizations();

    synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    assertThat(storage.listKeys(KEY)).as("kept on commit").containsExactly(KEY);
    synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    assertThat(storage.listKeys(KEY)).isEmpty();
  }

  private static TakeOnSource source() {
    return new TakeOnSource(
        UUID.randomUUID(),
        TakeOnSection.MAIN,
        "clients.csv",
        "text/csv",
        TakeOnSource.FileFormat.CSV,
        1,
        "0".repeat(64),
        null,
        "CSV",
        1,
        Map.of(),
        UUID.randomUUID());
  }
}

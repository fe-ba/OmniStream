package co.omnistream.presentation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.port.in.ICatalogManagement;
import co.omnistream.domain.port.in.IEventDispatch;
import co.omnistream.presentation.rest.Recording;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduledTasksTest {

  private static final UserId SYSTEM = new UserId(UUID.randomUUID());

  @Test
  void reindexScansTheRootAsTheExecutor() {
    Recording<ICatalogManagement> catalog =
        Recording.of(ICatalogManagement.class).answer("reindexLibrary", 3);

    new ReindexTask(catalog.port(), Path.of("/library")).execute(SYSTEM);

    assertThat(catalog.onlyCall("reindexLibrary").args())
        .containsExactly(SYSTEM, Path.of("/library"));
  }

  @Test
  void backupCopiesTheCatalogAsTheExecutor() {
    Recording<ICatalogManagement> catalog =
        Recording.of(ICatalogManagement.class).answer("backupCatalog", Path.of("/backups/c.db"));

    new BackupTask(catalog.port(), Path.of("/backups")).execute(SYSTEM);

    assertThat(catalog.onlyCall("backupCatalog").args())
        .containsExactly(SYSTEM, Path.of("/backups"));
  }

  @Test
  void syncPublishesPendingEventsUpToItsLimit() {
    Recording<IEventDispatch> dispatch =
        Recording.of(IEventDispatch.class).answer("publishPending", 0);

    new SyncTask(dispatch.port(), 50).execute(SYSTEM);

    assertThat(dispatch.onlyCall("publishPending").args()).containsExactly(50);
  }

  @Test
  void syncNeedsAPositiveLimit() {
    Recording<IEventDispatch> dispatch = Recording.of(IEventDispatch.class);

    assertThatThrownBy(() -> new SyncTask(dispatch.port(), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void eachTaskHasItsOwnName() {
    ICatalogManagement catalog = Recording.of(ICatalogManagement.class).port();
    IEventDispatch dispatch = Recording.of(IEventDispatch.class).port();

    assertThat(new ReindexTask(catalog, Path.of("/l")).name()).isEqualTo("reindex");
    assertThat(new BackupTask(catalog, Path.of("/b")).name()).isEqualTo("backup");
    assertThat(new SyncTask(dispatch, 1).name()).isEqualTo("sync");
  }
}

package co.omnistream.presentation.scheduler;

import co.omnistream.domain.model.access.UserId;
import co.omnistream.domain.port.in.ICatalogManagement;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Copies the catalog into the backup folder. */
public final class BackupTask implements ScheduledTask {

  private static final Logger LOG = LoggerFactory.getLogger(BackupTask.class);

  private final ICatalogManagement catalogManagement;
  private final Path destination;

  public BackupTask(ICatalogManagement catalogManagement, Path destination) {
    this.catalogManagement = Objects.requireNonNull(catalogManagement, "catalogManagement");
    this.destination = Objects.requireNonNull(destination, "destination");
  }

  @Override
  public String name() {
    return "backup";
  }

  @Override
  public void execute(UserId executor) {
    Path copy = catalogManagement.backupCatalog(executor, destination);
    LOG.info("Catalog backed up to {}", copy);
  }
}

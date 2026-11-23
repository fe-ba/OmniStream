package co.omnistream.infrastructure.persistence;

import co.omnistream.infrastructure.database.Database;
import co.omnistream.infrastructure.database.PersistenceException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * JDBC adapter of the server profile: it persists in PostgreSQL (ADR 0015) and backs the catalog up
 * with {@code pg_dump}, which has to be installed next to the application.
 */
public final class PostgresPersistenceAdapter extends JdbcPersistenceAdapter {

  public PostgresPersistenceAdapter(Database database) {
    super(database);
  }

  /** A PostgreSQL database from its JDBC URL, with the credentials it needs. */
  public static PostgresPersistenceAdapter at(
      URI jdbcUrl, Optional<String> user, Optional<String> password) {
    return new PostgresPersistenceAdapter(Database.postgres(jdbcUrl, user, password));
  }

  @Override
  protected void writeDump(Path destination) {
    String connection = database().url().substring("jdbc:".length());
    List<String> command =
        List.of(
            "pg_dump",
            "--dbname=" + connection,
            "--file=" + destination.toAbsolutePath(),
            "--no-owner",
            "--no-privileges");
    ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
    try {
      Process process = builder.start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (!process.waitFor(60, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new PersistenceException("pg_dump took too long");
      }
      if (process.exitValue() != 0) {
        throw new PersistenceException("pg_dump failed: " + output.strip());
      }
    } catch (IOException e) {
      throw new PersistenceException("Cannot run pg_dump; is it installed?", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PersistenceException("Interrupted while running pg_dump", e);
    }
  }
}
